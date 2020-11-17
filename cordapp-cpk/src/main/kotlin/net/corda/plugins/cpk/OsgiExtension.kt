package net.corda.plugins.cpk

import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.util.StringJoiner

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate", "unused")
open class OsgiExtension(project: Project, jar: Jar) {
    private val _exports: SetProperty<String> = project.objects.setProperty(String::class.java)
    private val _imports: SetProperty<String> = project.objects.setProperty(String::class.java)
        .apply(HasConfigurableValue::finalizeValueOnRead)
    private val _embeddeds: SetProperty<FileSystemLocation> = project.objects.setProperty(FileSystemLocation::class.java)
        .apply(HasConfigurableValue::finalizeValueOnRead)

    fun exportPackage(packageNames: Iterable<String>) {
        _exports.addAll(packageNames)
    }

    fun exportPackage(vararg packageNames: String) {
        exportPackage(packageNames.toList())
    }

    fun exportPackage(packageName: Provider<String>) {
        _exports.add(packageName)
    }

    fun exportAll(packageNames: Provider<out Iterable<String>>) {
        _exports.addAll(packageNames)
    }

    fun importPackage(packageNames: Iterable<String>) {
        _imports.addAll(packageNames)
    }

    fun importPackage(vararg packageNames: String) {
        importPackage(packageNames.toList())
    }

    fun importPackage(packageName: Provider<String>) {
        _imports.add(packageName)
    }

    fun optionalImport(packageNames: Iterable<String>) {
        _imports.addAll(packageNames.map { "$it;resolution:=optional" })
    }

    fun optionalImport(vararg packageNames: String) {
        optionalImport(packageNames.toList())
    }

    fun optionalImport(packageName: Provider<String>) {
        importPackage(packageName.map { "$it;resolution:=optional" })
    }

    fun suppressImportVersion(packageNames: Iterable<String>) {
        optionalImport(packageNames.map { "$it;version='[0,0)'" })
    }

    fun suppressImportVersion(vararg packageNames: String) {
        suppressImportVersion(packageNames.toList())
    }

    fun suppressImportVersion(packageName: Provider<String>) {
        optionalImport(packageName.map { "$it;version='[0,0)'" })
    }

    fun embed(files: Provider<Set<FileSystemLocation>>) {
        _embeddeds.addAll(files)
    }

    @get:Input
    val autoExport: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

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
