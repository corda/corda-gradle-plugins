@file:JvmName("CordappUtils")
package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.XMLFactory.createDocumentBuilderFactory
import net.corda.plugins.cpk2.XMLFactory.createTransformerFactory
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.specs.Spec
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.xml.transform.OutputKeys.ENCODING
import javax.xml.transform.OutputKeys.INDENT
import javax.xml.transform.OutputKeys.METHOD
import javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION
import javax.xml.transform.OutputKeys.STANDALONE
import javax.xml.transform.TransformerException
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val CORDAPP_CPK_PLUGIN_ID = "net.corda.plugins.cordapp-cpk2"
const val CORDAPP_TASK_GROUP = "Cordapp"
const val CPK_XML_NAMESPACE = "urn:corda-cpk"

const val CORDA_API_GROUP = "net.corda"
const val ENTERPRISE_API_GROUP = "com.r3.corda"

const val CORDAPP_SEALING_SYSTEM_PROPERTY_NAME = "net.corda.cordapp.sealing.enabled"

const val CPK_FILE_EXTENSION = "jar"
const val CPK_ARTIFACT_CLASSIFIER = "cordapp"

const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDAPP_EXTERNAL_CONFIGURATION_NAME = "cordappExternal"
const val CORDAPP_PACKAGING_CONFIGURATION_NAME = "cordappPackaging"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_ALL_PROVIDED_CONFIGURATION_NAME = "cordaAllProvided"
const val CORDA_PRIVATE_CONFIGURATION_NAME = "cordaPrivateProvided"
const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
const val CORDA_EMBEDDED_CONFIGURATION_NAME = "cordaEmbedded"
const val ALL_CORDAPPS_CONFIGURATION_NAME = "allCordapps"
const val CORDA_CPK_CONFIGURATION_NAME = "cordaCPK"

const val CPK_DEPENDENCIES = "META-INF/CPKDependencies"

// These tags are for the CPK file.
const val CPK_PLATFORM_VERSION = "Corda-CPK-Built-Platform-Version"
const val CPK_CORDAPP_NAME = "Corda-CPK-Cordapp-Name"
const val CPK_CORDAPP_VERSION = "Corda-CPK-Cordapp-Version"
const val CPK_CORDAPP_LICENCE = "Corda-CPK-Cordapp-Licence"
const val CPK_CORDAPP_VENDOR = "Corda-CPK-Cordapp-Vendor"
const val CPK_FORMAT_TAG = "Corda-CPK-Format"
const val CPK_FORMAT = "2.0"

// These tags are for the "main" JAR file.
const val PLATFORM_VERSION_X = 999
const val CORDA_CONTRACT_CLASSES = "Corda-Contract-Classes"
const val CORDA_WORKFLOW_CLASSES = "Corda-Flow-Classes"
const val CORDA_MAPPED_SCHEMA_CLASSES = "Corda-MappedSchema-Classes"
const val CORDA_SERVICE_CLASSES = "Corda-Service-Classes"
const val IMPORT_POLICY_PACKAGES = "Import-Policy-Packages"
const val REQUIRED_PACKAGES = "Required-Packages"

const val CORDAPP_PLATFORM_VERSION = "Cordapp-Built-Platform-Version"
const val CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name"
const val CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version"
const val CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name"
const val CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version"
const val CORDA_CPK_TYPE = "Corda-CPK-Type"

/**
 * Location of official R3 documentation for building CPKs and CPBs.
 */
const val CORDAPP_DOCUMENTATION_URL = "https://docs.corda.net/cordapp-build-systems.html"

@JvmField
val SEPARATOR: String = System.lineSeparator() + "- "

fun Dependency.toMaven(): String {
    val builder = StringBuilder()
    group?.also { builder.append(it).append(':') }
    builder.append(name)
    version?.also { builder.append(':').append(it) }
    return builder.toString()
}

fun ConfigurationContainer.createBasicConfiguration(name: String): Configuration {
    return maybeCreate(name)
        .setVisible(false)
        .also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
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
    return createBasicConfiguration(name).also { configuration ->
        getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        matching { it.name.endsWith(testSuffix) }.configureEach { cfg ->
            cfg.extendsFrom(configuration)
        }
    }
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
 * Extracts the [Manifest] from a jar file.
 */
val File.manifest: Manifest get() = JarFile(this).use(JarFile::getManifest)

/**
 * Computes the maximum value of [attributeName] from all [Manifest]s,
 * or `null` if no such value can be derived. The attribute is assumed
 * to have an integer value.
 */
fun Iterable<File>.maxOf(attributeName: String): Int? {
    return mapNotNull { it.manifest.mainAttributes.getValue(attributeName)?.toIntOrNull() }.maxOrNull()
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
 * Get a [MessageDigest] for [algorithmName], and handle
 * any [NoSuchAlgorithmException].
 */
fun digestFor(algorithmName: String): MessageDigest {
    return try {
        MessageDigest.getInstance(algorithmName)
    } catch (_ : NoSuchAlgorithmException) {
        throw InvalidUserDataException("Hash algorithm $algorithmName not available")
    }
}

private const val EOF = -1

/**
 * Compute hash for contents of [InputStream].
 */
@Throws(IOException::class)
fun MessageDigest.hashFor(input: InputStream): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val length = input.read(buffer)
        if (length == EOF) {
            break
        }
        update(buffer, 0, length)
    }
    return digest()
}

/**
 * Helper functions for XML documents.
 * Note that creating factories is EXPENSIVE.
 */
private val documentBuilderFactory = createDocumentBuilderFactory()

fun createXmlDocument(): Document {
    return documentBuilderFactory.newDocumentBuilder().newDocument().apply {
        xmlStandalone = true
    }
}

private val transformerFactory = createTransformerFactory()

@Throws(TransformerException::class)
fun Document.writeTo(writer: Writer) {
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(METHOD, "xml")
    transformer.setOutputProperty(ENCODING, "UTF-8")
    transformer.setOutputProperty(INDENT, "yes")
    transformer.setOutputProperty(STANDALONE, "yes")
    transformer.setOutputProperty(OMIT_XML_DECLARATION, "no")
    transformer.transform(DOMSource(this), StreamResult(writer))
}

fun Document.createRootElement(namespace: String, name: String): Element {
    val rootElement = createElementNS(namespace, name)
    appendChild(rootElement)
    return rootElement
}

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
