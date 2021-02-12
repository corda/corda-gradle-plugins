@file:JvmName("CordappUtils")
package net.corda.plugins.cpk

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import org.gradle.api.specs.Spec
import java.util.Collections.unmodifiableSet

const val GROUP_NAME = "Cordapp"

const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDAPP_PACKAGING_CONFIGURATION_NAME = "cordappPackaging"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_ALL_PROVIDED_CONFIGURATION_NAME = "cordaAllProvided"
const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
const val CORDA_EMBEDDED_CONFIGURATION_NAME = "cordaEmbedded"
const val ALL_CORDAPPS_CONFIGURATION_NAME = "allCordapps"
const val CORDA_CPK_CONFIGURATION_NAME = "cordaCPK"

val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
    "org.jetbrains.kotlin" to "*",
    "net.corda.kotlin" to "*",
    "org.osgi" to "*",
    "org.slf4j" to "slf4j-api",
    "org.slf4j" to "jcl-over-slf4j",
    "commons-logging" to "commons-logging",
    "co.paralleluniverse" to "quasar-core",
    "co.paralleluniverse" to "quasar-core-osgi"
))

private fun ConfigurationContainer.createChildConfiguration(name: String, parent: Configuration): Configuration {
    return findByName(name) ?: run {
        val configuration = create(name).setTransitive(false)
        parent.extendsFrom(configuration)
        configuration
    }
}

/**
 * This configuration will contribute to the CorDapp's compile classpath
 * but not to its runtime classpath. However, it will still contribute
 * to all TESTING classpaths.
 */
fun ConfigurationContainer.createCompileConfiguration(name: String): Configuration {
    return createCompileConfiguration(name, "Implementation")
}

private fun ConfigurationContainer.createCompileConfiguration(name: String, testSuffix: String): Configuration {
    return findByName(name) ?: run {
        val configuration = create(name)
        getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        matching { it.name.endsWith(testSuffix) }.configureEach { cfg ->
            cfg.extendsFrom(configuration)
        }
        configuration
    }
}

fun ConfigurationContainer.createRuntimeOnlyConfiguration(name: String): Configuration {
    return createChildConfiguration(name, getByName(RUNTIME_ONLY_CONFIGURATION_NAME))
}

/**
 * Identify the artifacts that were resolved for these [Dependency] objects,
 * including all of their transitive dependencies.
 */
fun ResolvedConfiguration.resolveAll(dependencies: Collection<Dependency>): Set<ResolvedArtifact> {
    return resolve(dependencies, ResolvedDependency::getAllModuleArtifacts)
}

/**
 * Identify the artifacts that were resolved for these [Dependency] objects only.
 * This does not include any transitive dependencies.
 */
fun ResolvedConfiguration.resolveFirstLevel(dependencies: Collection<Dependency>): Set<ResolvedArtifact> {
    return resolve(dependencies, ResolvedDependency::getModuleArtifacts)
}

private fun ResolvedConfiguration.resolve(dependencies: Collection<Dependency>, fetchArtifacts: (ResolvedDependency) -> Iterable<ResolvedArtifact>): Set<ResolvedArtifact> {
    return getFirstLevelModuleDependencies(Spec(dependencies::contains))
        .flatMapTo(LinkedHashSet(), fetchArtifacts)
}

/**
 * Check whether every [String] in this [List] is
 * a valid Java identifier.
 */
val List<String>.isJavaIdentifiers: Boolean get() {
    return this.all(String::isJavaIdentifier)
}

/**
 * Checks whether this [String] could be considered to
 * be a valid identifier in Java. Identifiers are only
 * permitted to contain a specific subset of [Character]s.
 */
val String.isJavaIdentifier: Boolean get() {
    if (isEmpty() || !Character.isJavaIdentifierStart(this[0])) {
        return false
    }

    var idx = length
    while (--idx > 0) {
        if (!Character.isJavaIdentifierPart(this[idx])) {
            return false
        }
    }

    return true
}
