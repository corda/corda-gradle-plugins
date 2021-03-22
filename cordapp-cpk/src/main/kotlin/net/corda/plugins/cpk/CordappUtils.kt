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
import org.w3c.dom.Element
import java.io.File
import java.util.Collections.unmodifiableSet
import java.util.jar.JarFile

const val CORDAPP_CPK_PLUGIN_ID = "net.corda.plugins.cordapp-cpk"
const val GROUP_NAME = "Cordapp"

const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDAPP_PACKAGING_CONFIGURATION_NAME = "cordappPackaging"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_ALL_PROVIDED_CONFIGURATION_NAME = "cordaAllProvided"
const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
const val CORDA_EMBEDDED_CONFIGURATION_NAME = "cordaEmbedded"
const val ALL_CORDAPPS_CONFIGURATION_NAME = "allCordapps"
const val CORDA_CPK_CONFIGURATION_NAME = "cordaCPK"

const val CPK_CORDAPP_NAME = "Corda-CPK-Cordapp-Name"
const val CPK_CORDAPP_VERSION = "Corda-CPK-Cordapp-Version"
const val CPK_CORDAPP_LICENCE = "Corda-CPK-Cordapp-Licence"
const val CPK_CORDAPP_VENDOR = "Corda-CPK-Cordapp-Vendor"
const val CPK_FORMAT_TAG = "Corda-CPK-Format"
const val CPK_FORMAT = "1.0"

const val CORDA_CONTRACT_CLASSES = "Corda-Contract-Classes"
const val CORDA_WORKFLOW_CLASSES = "Corda-Flow-Classes"
const val CORDA_MAPPED_SCHEMA_CLASSES = "Corda-MappedSchema-Classes"
const val CORDA_SERVICE_CLASSES = "Corda-Service-Classes"
const val REQUIRED_PACKAGES = "Required-Packages"

const val CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name"
const val CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version"
const val CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name"
const val CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version"

@JvmField
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

@JvmField
val SEPARATOR: String = System.lineSeparator() + "- "

fun Dependency.toMaven(): String {
    val builder = StringBuilder()
    group?.also { builder.append(it).append(':') }
    builder.append(name)
    version?.also { builder.append(':').append(it) }
    return builder.toString()
}

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
        val configuration = maybeCreate(name)
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
 * Extracts the package names from a [JarFile].
 */
private val MULTI_RELEASE = "^META-INF/versions/\\d++/(.++)\$".toRegex()
private const val DIRECTORY_SEPARATOR = '/'
private const val PACKAGE_SEPARATOR = '.'

val File.packages: MutableSet<String> get() {
    return JarFile(this).use { jar ->
        val packages = mutableSetOf<String>()
        val jarEntries = jar.entries()
        while (jarEntries.hasMoreElements()) {
            val jarEntry = jarEntries.nextElement()
            if (!jarEntry.isDirectory) {
                val entryName = jarEntry.name
                if (entryName.startsWith("OSGI-INF/")) {
                    continue
                }

                val binaryFQN = if (entryName.startsWith("META-INF/")) {
                    (MULTI_RELEASE.matchEntire(entryName) ?: continue).groupValues[1]
                } else {
                    entryName
                }
                val binaryPackageName = binaryFQN.substringBeforeLast(DIRECTORY_SEPARATOR)
                if (isValidPackage(binaryPackageName)) {
                    packages.add(binaryPackageName.toPackageName())
                }
            }
        }
        packages
    }
}

private fun isValidPackage(name: String): Boolean {
    return name.split(DIRECTORY_SEPARATOR).all(String::isJavaIdentifier)
}

private fun String.toPackageName(): String {
    return replace(DIRECTORY_SEPARATOR, PACKAGE_SEPARATOR)
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

/**
 * Helper functions for XML documents.
 */
fun Element.appendElement(name: String): Element {
    val childElement = ownerDocument.createElement(name)
    appendChild(childElement)
    return childElement
}

fun Element.appendElement(name: String, value: String?): Element {
    return appendElement(name).also { child ->
        child.appendChild(ownerDocument.createTextNode(value))
    }
}
