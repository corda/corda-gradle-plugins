@file:JvmName("CPKDependency")
package net.corda.plugins.cpk2

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import java.util.Collections.unmodifiableSet

internal class CordappDependencyCollector(
    private val configurations: ConfigurationContainer,
    private val dependencyHandler: DependencyHandler,
    private val attributor: Attributor,
    private val logger: Logger
) {
    private val platforms = mutableSetOf<ModuleDependency>()
    private val cordappProjects = mutableSetOf<ProjectDependency>()
    private val cordappModules = mutableSetOf<Dependency>()
    private val provided = mutableSetOf<Dependency>()

    val cordappDependencies: Set<Dependency> @Synchronized get() {
        return unmodifiableSet(cordappModules + cordappProjects)
    }

    val providedDependencies: Set<Dependency> @Synchronized get() {
        return unmodifiableSet(provided)
    }

    @Synchronized
    fun collect() {
        // Identify any Gradle platform dependencies we may have.
        // We will need these when we resolve our detached configurations.
        // IMPORTANT! We MUST NOT resolve the "cordappExternal" configuration here!
        configurations.getByName(CORDAPP_EXTERNAL_CONFIGURATION_NAME).allDependencies
            .filterIsInstance(ModuleDependency::class.java)
            .filterTo(platforms, ::isPlatformModule)

        // Now walk through our "cordapp" dependencies.
        collectFrom(configurations.getByName(CORDAPP_CONFIGURATION_NAME).allDependencies)
    }

    private fun resolve(dependency: Dependency): ResolvedConfiguration {
        // Configuration.extendsFrom() appears broken for detached configurations.
        // See https://github.com/gradle/gradle/issues/6881.
        return configurations.detachedConfiguration(dependency, *platforms.toTypedArray())
            .attributes(attributor::forCompileClasspath)
            .setVisible(false)
            .resolvedConfiguration
    }

    private fun hasCollectables(dependency: ModuleDependency): Boolean {
        return synchronized(dependency) {
            dependency.isTransitive || dependency.attributes.contains(TRANSITIVE_ATTRIBUTE)
        } && !isPlatformModule(dependency)
    }

    private fun collectFrom(dependencies: DependencySet) {
        for (dependency in dependencies) {
            if (dependency is ProjectDependency) {
                if (cordappProjects.add(dependency) && hasCollectables(dependency)) {
                    // Resolve a CorDapp dependency from another
                    // module in a multi-module build.
                    collectFrom(dependency)
                }
            } else if (dependency is ModuleDependency) {
                if (cordappModules.add(dependency) && hasCollectables(dependency)) {
                    val depMap = dependency.asMap
                    if (dependency.version == null && platforms.isNotEmpty()) {
                        // This dependency has no explicit version of its own.
                        // Try to learn which version to use by resolving it
                        // against our platform dependencies.
                        resolve(dependency).getFirstLevelModuleDependencies { dep ->
                            dep.group == dependency.group && dep.name == dependency.name
                        }.singleOrNull()?.also { dep ->
                            depMap[DEPENDENCY_VERSION] = dep.moduleVersion
                        }
                    }
                    val cordapp = dependencyHandler.create(depMap.toCPK())
                    // Try to resolve the CorDapp's "companion" dependency.
                    // This may not exist, although it's better if it does.
                    collectFrom(cordapp)
                }
            }
        }
    }

    private fun collectFrom(cordapp: ProjectDependency) {
        val cordappConfigurations = cordapp.dependencyProject.configurations
        cordappConfigurations.findByName(CORDAPP_CONFIGURATION_NAME)?.also { transitives ->
            collectFrom(transitives.allDependencies)
        }
        cordappConfigurations.findByName(CORDA_PROVIDED_CONFIGURATION_NAME)?.also { transitives ->
            provided.addAll(transitives.allDependencies)
        }
    }

    private fun collectFrom(cordapp: Dependency) {
        val resolved = resolve(cordapp)
        if (resolved.hasError()) {
            logger.warn("CorDapp has unresolved dependencies:{}",
                resolved.lenientConfiguration.unresolvedModuleDependencies.joinToString(SEPARATOR, SEPARATOR))
            logger.warn("Cannot resolve CPK companion artifact '{}' - SKIPPED", cordapp.toMaven())
        } else {
            // This should never now throw ResolveException.
            collectFrom(resolved.getFirstLevelModuleDependencies { dep ->
                dep is ModuleDependency && !isPlatformModule(dep)
            }, mutableSetOf())
        }
    }

    private fun collectFrom(resolvedDeps: Set<ResolvedDependency>, result: MutableSet<ResolvedDependency>) {
        for (resolved in resolvedDeps) {
            if (result.add(resolved)) {
                val dependency = resolved.asMap
                if (dependency.isCPK) {
                    cordappModules.add(dependencyHandler.create(dependency.toCordapp()))
                    collectFrom(resolved.children, result)
                } else {
                    provided.add(dependencyHandler.create(dependency))
                }
            }
        }
    }
}

private const val CPK_SUFFIX = ".corda.cpk"
private const val DEPENDENCY_GROUP = "group"
private const val DEPENDENCY_NAME = "name"
private const val DEPENDENCY_VERSION = "version"

private val ResolvedDependency.asMap: MutableMap<String, String> get() {
    return mutableMapOf(DEPENDENCY_NAME to moduleName, DEPENDENCY_GROUP to moduleGroup, DEPENDENCY_VERSION to moduleVersion)
}

private val Dependency.asMap: MutableMap<String, String> get() {
    return mutableMapOf(DEPENDENCY_NAME to name).apply {
        group?.also { put(DEPENDENCY_GROUP, it) }
        version?.also { put(DEPENDENCY_VERSION, it) }
    }
}

private fun toCpkPrefix(group: String?): String {
    return group?.let { "$it." } ?: ""
}

private fun String.hasSuffix(suffix: String): Boolean {
    return if (length == suffix.length) {
        this == suffix
    } else {
        val idx = length - suffix.length - 1
        idx > 0 && endsWith(suffix) && this[idx] == '.'
    }
}

private val Map<String, String>.isCPK: Boolean get() {
    val group = get(DEPENDENCY_GROUP) ?: return false
    val name = get(DEPENDENCY_NAME)!!
    return name.length > CPK_SUFFIX.length
        && name.endsWith(CPK_SUFFIX)
        && group.hasSuffix(name.dropLast(CPK_SUFFIX.length))
}

/**
 * This should only be invoked if [isCPK] has already returned `true`.
 */
private fun MutableMap<String, String>.toCordapp(): MutableMap<String, String> {
    val cordappName = get(DEPENDENCY_NAME)!!.removeSuffix(CPK_SUFFIX)
    val cpkGroup = get(DEPENDENCY_GROUP)!!
    if (cpkGroup.length == cordappName.length) {
        remove(DEPENDENCY_GROUP)
    } else {
        put(DEPENDENCY_GROUP, cpkGroup.dropLast(cordappName.length + 1))
    }
    put(DEPENDENCY_NAME, cordappName)
    return this
}

private fun MutableMap<String, String>.toCPK(): MutableMap<String, String> {
    val artifactName = get(DEPENDENCY_NAME)!!
    put(DEPENDENCY_GROUP, toCompanionGroupId(get(DEPENDENCY_GROUP), artifactName))
    put(DEPENDENCY_NAME, toCompanionArtifactId(artifactName))
    return this
}

fun toCompanionGroupId(group: String?, artifactId: String): String {
    return "${toCpkPrefix(group)}$artifactId"
}

fun toCompanionArtifactId(artifactId: String): String {
    return "$artifactId$CPK_SUFFIX"
}
