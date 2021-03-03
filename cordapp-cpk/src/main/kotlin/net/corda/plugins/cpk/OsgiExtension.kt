package net.corda.plugins.cpk

import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Jar
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.io.IOException
import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableSet
import java.util.Properties
import java.util.StringJoiner

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate", "unused")
open class OsgiExtension(objects: ObjectFactory, project: Project, jar: Jar) {
    private companion object {
        private const val CORDA_CLASSES_VERSION = "net.corda.release.version"
        private const val CORDA_CLASSES_FILENAME = "corda-classes"

        val BASE_CORDA_CLASSES: Map<String, String> = unmodifiableMap(mapOf(
            CORDA_CONTRACT_CLASSES to "IMPLEMENTS;net.corda.v5.ledger.contracts.Contract",
            CORDA_WORKFLOW_CLASSES to "IMPLEMENTS;net.corda.core.flows.Flow",
            CORDA_MAPPED_SCHEMA_CLASSES to "EXTENDS;net.corda.core.node.services.persistence.MappedSchema",
            CORDA_SERIALIZATION_WHITELIST_CLASSES to "IMPLEMENTS;net.corda.core.serialization.SerializationWhitelist",
            CORDA_CHECKPOINT_CUSTOM_SERIALIZER_CLASSES to "IMPLEMENTS;net.corda.core.serialization.CheckpointCustomSerializer",
            CORDA_SERIALIZATION_CUSTOM_SERIALIZER_CLASSES to "IMPLEMENTS;net.corda.v5.serialization.SerializationCustomSerializer",
            CORDA_SERVICE_CLASSES to "IMPLEMENTS;net.corda.core.serialization.SerializeAsToken;HIERARCHY_INDIRECTLY_ANNOTATED;net.corda.core.node.services.CordaService"
        ))

        val requiredPackages: Set<String> = unmodifiableSet(setOf(
            "org.hibernate.annotations",
            "org.hibernate.proxy",
            "javassist.util.proxy"
        ))

        /**
         * Load any external set of class scanning instructions for Bnd.
         * We will read them from an optional properties file.
         */
        @Throws(IOException::class)
        fun loadCordaClasses(project: Project): Map<String, String> {
            val cordaReleaseVersion = project.findProperty(CORDA_CLASSES_VERSION)?.toString()?.trim()
            val classesFile = project.file(dashConcat(CORDA_CLASSES_FILENAME, cordaReleaseVersion) + ".properties")
            return if (classesFile.isFile) {
                Properties().let { props ->
                    classesFile.inputStream().buffered().use(props::load)
                    props.entries.associate { entry ->
                        entry.key.toString() to entry.value.toString().trim()
                    }
                }
            } else {
                emptyMap()
            }
        }

        fun dashConcat(first: String, second: String?): String {
            return if (second.isNullOrEmpty()) {
                first
            } else {
                "$first-$second"
            }
        }

        fun dynamic(value: String): String = "$value;resolution:=dynamic;version=!"
        fun optional(value: String): String = "$value;resolution:=optional"
        fun emptyVersion(value: String): String = "$value;version='[0,0)'"
    }

    private val _cordaClasses: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java).apply {
        putAll(BASE_CORDA_CLASSES)
        putAll(loadCordaClasses(project.rootProject))
    }
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
            val includeResource = StringJoiner(",", "-includeresource:", System.lineSeparator())
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
    val imports: Provider<String> = _imports.map(::declareImports)

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
    val symbolicName: Provider<String>

    init {
        val groupName = project.provider { project.group.toString() }
        val archiveName = createArchiveName(jar)
        symbolicName = groupName.zip(archiveName) { group, name ->
            if (group.isEmpty()) {
                name
            } else {
                "$group.$name"
            }
        }

        /**
         * We need to import these packages so that the OSGi framework
         * will create bundle wirings for them. This allows Hibernate
         * to create lazy proxies for any JPA entities inside the CPK.
         *
         * We DO NOT want to bind the CPK to use specific versions of
         * either Hibernate or Javassist here.
         */
        importPackages(requiredPackages.map(::dynamic))
    }

    private fun createArchiveName(jar: Jar): Provider<String> {
        return jar.archiveBaseName.zip(jar.archiveAppendix.orElse(""), ::dashConcat)
            .zip(jar.archiveClassifier.orElse(""), ::dashConcat)
    }
}
