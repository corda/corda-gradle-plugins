@file:JvmName("CPKDependency")
package net.corda.plugins.cpk

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import java.util.Collections.unmodifiableSet

class CordappDependencyCollector(
    private val configurations: ConfigurationContainer,
    private val dependencyHandler: DependencyHandler
) {
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
        collectFrom(configurations.getByName(CORDAPP_CONFIGURATION_NAME).allDependencies)
    }

    private fun collectFrom(dependencies: DependencySet) {
        for (dependency in dependencies) {
            if (dependency is ProjectDependency) {
                if (cordappProjects.add(dependency)) {
                    collectFrom(dependency)
                }
            } else if (dependency is ModuleDependency) {
                if (cordappModules.add(dependency)) {
                    val cordapp = dependencyHandler.create(dependency.asMap.toCPK())
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
        val resolved = configurations.detachedConfiguration(cordapp).resolvedConfiguration
        collectFrom(resolved.firstLevelModuleDependencies, mutableSetOf())
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

private val Map<String, String>.cpkPrefix: String get() {
    return toCpkPrefix(get(DEPENDENCY_GROUP))
}

private val Map<String, String>.isCPK: Boolean get() {
    val name = get(DEPENDENCY_NAME)!!
    return name.startsWith(cpkPrefix) && name.endsWith(CPK_SUFFIX)
}

private fun MutableMap<String, String>.toCordapp(): MutableMap<String, String> {
    put(DEPENDENCY_NAME, get(DEPENDENCY_NAME)!!.removePrefix(cpkPrefix).removeSuffix(CPK_SUFFIX))
    return this
}

private fun MutableMap<String, String>.toCPK(): MutableMap<String, String> {
    put(DEPENDENCY_NAME, toCompanionArtifactId(get(DEPENDENCY_GROUP), get(DEPENDENCY_NAME)!!))
    return this
}

fun toCompanionArtifactId(group: String?, artifactId: String): String {
    return "${toCpkPrefix(group)}$artifactId$CPK_SUFFIX"
}
