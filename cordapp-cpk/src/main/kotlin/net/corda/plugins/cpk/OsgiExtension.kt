package net.corda.plugins.cpk

import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.util.Collections.unmodifiableMap
import java.util.StringJoiner

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate", "unused")
open class OsgiExtension(objects: ObjectFactory, project: Project, jar: Jar) {
    private companion object {
        val cordaClasses: Map<String, String> = unmodifiableMap(mapOf(
            CORDA_CONTRACT_CLASSES to "IMPLEMENTS;net.corda.core.contracts.Contract",
            CORDA_WORKFLOW_CLASSES to "IMPLEMENTS;net.corda.core.flows.Flow",
            CORDA_MAPPED_SCHEMA_CLASSES to "IMPLEMENTS;net.corda.core.schemas.MappedSchema",
            CORDA_SERIALIZATION_WHITELIST_CLASSES to "IMPLEMENTS;net.corda.core.serialization.SerializationWhitelist",
            CORDA_CHECKPOINT_CUSTOM_SERIALIZER_CLASSES to "IMPLEMENTS;net.corda.core.serialization.CheckpointCustomSerializer",
            CORDA_SERIALIZATION_CUSTOM_SERIALIZER_CLASSES to "IMPLEMENTS;net.corda.v5.serialization.SerializationCustomSerializer",
            CORDA_SERVICE_CLASSES to "IMPLEMENTS;net.corda.core.serialization.SerializeAsToken;HIERARCHY_INDIRECTLY_ANNOTATED;net.corda.core.node.services.CordaService"
        ))

        fun optional(value: String): String = "$value;resolution:=optional"
        fun emptyVersion(value: String): String = "$value;version='[0,0)'"
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

    fun embed(files: Provider<Set<FileSystemLocation>>) {
        _embeddeds.addAll(files)
    }

    @get:Input
    val autoExport: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    @get:Internal
    val exports: Provider<String> = _exports.map { names ->
        if (names.isNotEmpty()){
            names.joinToString(",", "-exportcontents:")
        } else {
            ""
        }
    }

    @get:Internal
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

    @get:Internal
    val imports: Provider<String> = _imports.map(::declareImports)

    private fun declareImports(importPackages: Set<String>): String {
        return if (importPackages.isNotEmpty()) {
            importPackages.joinToString(",", "$IMPORT_PACKAGE=", ",*")
        } else {
            ""
        }
    }

    @get:Internal
    val cordaScanning: Provider<String> = objects.property(String::class.java)
        .value(generateCordaClassQuery())
        .apply(Property<String>::finalizeValueOnRead)

    private fun generateCordaClassQuery(): String {
        val joiner = StringJoiner(System.lineSeparator())
        for (cordaClass in cordaClasses) {
            // This NAMED filter only identifies "anonymous" classes.
            // Adding STATIC removes all inner classes as well.
            joiner.add("${cordaClass.key}=\${classes;${cordaClass.value};CONCRETE;PUBLIC;STATIC;NAMED;!*\\.[\\\\d]+*}")
        }
        return joiner.toString()
    }

    @get:Internal
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
    }

    private fun createArchiveName(jar: Jar): Provider<String> {
        return jar.archiveBaseName.zip(jar.archiveAppendix.orElse(""), ::dashConcat)
            .zip(jar.archiveClassifier.orElse(""), ::dashConcat)
    }

    private fun dashConcat(first: String, second: String): String {
        return if (second.isEmpty()) {
            first
        } else {
            "$first-$second"
        }
    }
}
