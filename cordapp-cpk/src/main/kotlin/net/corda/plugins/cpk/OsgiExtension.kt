@file:JvmName("OsgiProperties")
package net.corda.plugins.cpk

import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskInputs
import org.gradle.api.tasks.bundling.Jar
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.io.IOException
import java.io.InputStream
import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableSet
import java.util.Properties
import java.util.StringJoiner

/**
 * Registers these [OsgiExtension] properties as task inputs,
 * because Gradle cannot "see" their `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, osgi: OsgiExtension) {
    property("${nestName}.autoExport", osgi.autoExport)
    property("${nestName}.exports", osgi.exports)
    property("${nestName}.embeddedJars", osgi.embeddedJars)
    property("${nestName}.applyImportPolicy", osgi.applyImportPolicy)
    property("${nestName}.imports", osgi.imports)
    property("${nestName}.scanCordaClasses", osgi.scanCordaClasses)
    property("${nestName}.symbolicName", osgi.symbolicName)
}

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate", "unused")
open class OsgiExtension(objects: ObjectFactory, jar: Jar) {
    private companion object {
        private const val CORDAPP_CONFIG_FILENAME = "cordapp-configuration.properties"

        private val CORDA_CLASSES = "^Corda-.+-Classes\$".toRegex()

        private val BASE_CORDA_CLASSES: Map<String, String> = unmodifiableMap(mapOf(
            CORDA_CONTRACT_CLASSES to "IMPLEMENTS;net.corda.v5.ledger.contracts.Contract",
            CORDA_WORKFLOW_CLASSES to "IMPLEMENTS;net.corda.v5.application.flows.Flow",
            CORDA_MAPPED_SCHEMA_CLASSES to "EXTENDS;net.corda.v5.persistence.MappedSchema",
            CORDA_SERVICE_CLASSES to "IMPLEMENTS;net.corda.v5.application.services.CordaService"
        ))

        /**
         * We need to import these packages so that the OSGi framework
         * will create bundle wirings for them. This allows Hibernate
         * to create lazy proxies for any JPA entities inside the CPK.
         *
         * We DO NOT want to bind the CPK to use specific versions of
         * either Hibernate or Javassist here.
         */
        private val BASE_REQUIRED_PACKAGES: Set<String> = unmodifiableSet(setOf(
            "org.hibernate.annotations",
            "org.hibernate.proxy"
        ))

        private fun getPackageRange(packages: Array<String>): IntRange {
            val firstIdx = if (packages.size > 2 && packages[0] == "META-INF" && packages[1] == "versions") {
                3
            } else {
                0
            }
            return firstIdx..packages.size - 2
        }

        @Throws(IOException::class)
        private fun loadConfig(input: InputStream): Map<String, String> {
            return Properties().let { props ->
                input.buffered().use(props::load)
                props.entries.associate { entry ->
                    entry.key.toString() to entry.value.toString().trim()
                }
            }
        }

        fun dashConcat(first: String, second: String?): String {
            return if (second.isNullOrEmpty()) {
                first
            } else {
                "$first-$second"
            }
        }

        fun parsePackages(value: String): Set<String> {
            return value.split(",").mapTo(LinkedHashSet(), String::trim)
        }

        fun consumerPolicy(value: String): String = "$value:o;version='\${range;[=,+);\${@}}'"
        fun dynamic(value: String): String = "$value;resolution:=dynamic;version=!"
        fun optional(value: String): String = "$value;resolution:=optional"
        fun emptyVersion(value: String): String = "$value;version='[0,0)'"
    }

    @get:Internal // Annotated for documentation purposes only.
    var configured: Boolean = false
        private set

    private val _noPackages: SetProperty<String> = objects.setProperty(String::class.java)
        .apply(SetProperty<String>::disallowChanges)
    private val _policyPackages: SetProperty<String> = objects.setProperty(String::class.java)
    private val _requiredPackages: SetProperty<String> = objects.setProperty(String::class.java).value(BASE_REQUIRED_PACKAGES)
    private val _cordaClasses: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
    private val _autoExportPackages: SetProperty<String> = objects.setProperty(String::class.java)
    private val _exports: SetProperty<String> = objects.setProperty(String::class.java)
    private val _imports: SetProperty<String> = objects.setProperty(String::class.java)
        .apply(SetProperty<String>::finalizeValueOnRead)
    private val _embeddeds: SetProperty<FileSystemLocation> = objects.setProperty(FileSystemLocation::class.java)
        .apply(SetProperty<FileSystemLocation>::finalizeValueOnRead)

    fun exportPackages(packageNames: Provider<out Iterable<String>>) {
        _exports.addAll(packageNames)
    }

    fun exportPackages(packageNames: Iterable<String>) {
        _exports.addAll(packageNames)
    }

    fun exportPackage(vararg packageNames: String) {
        exportPackages(packageNames.toList())
    }

    fun exportPackage(packageName: Provider<String>) {
        _exports.add(packageName)
    }

    fun importPackages(packageNames: Provider<out Iterable<String>>) {
        _imports.addAll(packageNames)
    }

    fun importPackages(packageNames: Iterable<String>) {
        _imports.addAll(packageNames)
    }

    fun importPackage(vararg packageNames: String) {
        importPackages(packageNames.toList())
    }

    fun importPackage(packageName: Provider<String>) {
        _imports.add(packageName)
    }

    fun optionalImports(packageNames: Iterable<String>) {
        importPackages(packageNames.map(::optional))
    }

    fun optionalImports(packageNames: Provider<out Iterable<String>>) {
        importPackages(packageNames.map { names -> names.map(::optional) })
    }

    fun optionalImport(vararg packageNames: String) {
        optionalImports(packageNames.toList())
    }

    fun optionalImport(packageName: Provider<String>) {
        importPackage(packageName.map(::optional))
    }

    fun suppressImportVersions(packageNames: Iterable<String>) {
        optionalImports(packageNames.map(::emptyVersion))
    }

    fun suppressImportVersion(vararg packageNames: String) {
        suppressImportVersions(packageNames.toList())
    }

    fun suppressImportVersion(packageName: Provider<String>) {
        optionalImport(packageName.map(::emptyVersion))
    }

    fun embed(files: Provider<out Set<FileSystemLocation>>) {
        _embeddeds.addAll(files)
    }

    @get:Input
    val autoExport: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    private val autoExported: Provider<out Set<String>>
        get() = autoExport.flatMap { isAuto ->
            if (isAuto) {
                _autoExportPackages
            } else {
                _noPackages
            }
        }

    @get:Input
    val exports: Provider<String> = _exports.map { names ->
        if (names.isNotEmpty()){
            names.joinToString(",", "-exportcontents:")
        } else {
            ""
        }
    }

    @get:Input
    val embeddedJars: Provider<String> = _embeddeds.map(::declareEmbeddedJars)

    private fun declareEmbeddedJars(locations: Set<FileSystemLocation>): String {
        return if (locations.isNotEmpty()) {
            val includeResource = StringJoiner(",", "-includeresource.cordapp:", System.lineSeparator())
            val bundleClassPath = StringJoiner(",", "$BUNDLE_CLASSPATH=", System.lineSeparator()).add(".")
            for (location in locations) {
                val file = location.asFile
                val embeddedJar = "lib/${file.name}"
                includeResource.add("$embeddedJar=${file.toURI()}")
                bundleClassPath.add(embeddedJar)
            }
            "$includeResource$bundleClassPath"
        } else {
            ""
        }
    }

    @get:Input
    val applyImportPolicy: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    private val activePolicy: Provider<out Set<String>>
        get() = applyImportPolicy.flatMap { isActive ->
            if (isActive) {
                _policyPackages
            } else {
                _noPackages
            }
        }

    @get:Input
    val imports: Provider<String> = _imports.zip(_requiredPackages) { imports, required ->
        val result = LinkedHashSet(imports)
        result.addAll(required.map(::dynamic))
        result
    }.zip(activePolicy) { imports, policy ->
        imports.addAll(policy.map(::consumerPolicy))
        imports
    }.map(::declareImports)

    private fun declareImports(importPackages: Set<String>): String {
        return if (importPackages.isNotEmpty()) {
            importPackages.joinToString(",", "$IMPORT_PACKAGE=", ",*")
        } else {
            ""
        }
    }

    @get:Input
    val scanCordaClasses: Provider<String> = objects.property(String::class.java)
        .value(_cordaClasses.map(::generateCordaClassQuery))
        .apply(Property<String>::finalizeValueOnRead)

    private fun generateCordaClassQuery(cordaClasses: Map<String, String>): String {
        return cordaClasses.map { cordaClass ->
            // This NAMED filter only identifies "anonymous" classes.
            // Adding STATIC removes all inner classes as well.
            "${cordaClass.key}=\${classes;${cordaClass.value};CONCRETE;PUBLIC;STATIC;NAMED;!*\\.[\\\\d]+*}"
        }.joinToString(System.lineSeparator())
    }

    @get:Input
    val symbolicName: Property<String> = objects.property(String::class.java)

    init {
        val project = jar.project
        val groupName = project.provider { project.group.toString().trim() }
        val archiveName = createArchiveName(jar)
        symbolicName.convention(groupName.zip(archiveName) { group, name ->
            if (group.isEmpty()) {
                name
            } else {
                "$group.$name"
            }
        })

        // Install a "listener" for files copied into the CorDapp jar.
        // We will extract the names of the non-empty packages inside
        // this jar as we go...
        jar.rootSpec.eachFile { file ->
            if (!file.isDirectory) {
                val elements = file.relativePath.segments
                val packageRange = getPackageRange(elements)
                if (!packageRange.isEmpty()) {
                    val packageName = elements.slice(packageRange)
                    if (packageName[0] != "META-INF"
                        && packageName[0] != "OSGI-INF"
                        && packageName[0] != "migration"
                        && packageName.isJavaIdentifiers) {
                        _autoExportPackages.add(packageName.joinToString("."))
                    }
                }
            }
        }

        // Add the auto-extracted package names to the exports.
        _exports.addAll(autoExported)

        _cordaClasses.putAll(BASE_CORDA_CLASSES)

        /**
         * Read an optional configuration file from a "friend" plugin:
         * ```
         *     net.corda.cordapp.cordapp-configuration
         * ```
         * This will allow us to keep our Bnd metadata instructions
         * up-to-date with future versions of Corda without also needing
         * to update the `cordapp-cpk` plugin itself. (Hopefully, anyway.)
         */
        configure(project.rootProject)
    }

    private fun configure(project: Project) {
        project.plugins.withId(CORDAPP_CONFIG_PLUGIN_ID) { plugin ->
            val config = loadConfig(plugin::class.java.getResourceAsStream(CORDAPP_CONFIG_FILENAME) ?: return@withId)
            _cordaClasses.putAll(config.filterKeys(CORDA_CLASSES::matches))

            /**
             * Replace the default set of required packages with any new ones.
             */
            config[REQUIRED_PACKAGES]?.let(::parsePackages)?.also(_requiredPackages::set)

            /**
             * Apply our OSGi "consumer policy" when importing these packages.
             */
            config[IMPORT_POLICY_PACKAGES]?.let(::parsePackages)?.also(_policyPackages::set)

            /**
             * Show we have received our configuration.
             */
            configured = true
        }
    }

    private fun createArchiveName(jar: Jar): Provider<String> {
        return jar.archiveBaseName.zip(jar.archiveAppendix.orElse(""), ::dashConcat)
            .zip(jar.archiveClassifier.orElse(""), ::dashConcat)
    }
}
