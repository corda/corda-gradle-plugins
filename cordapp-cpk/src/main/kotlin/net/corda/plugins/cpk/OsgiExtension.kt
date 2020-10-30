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
import java.util.StringJoiner

@Suppress("UnstableApiUsage", "unused")
open class OsgiExtension(project: Project, jar: Jar) {
    private val packages: SetProperty<String> = project.objects.setProperty(String::class.java)

    private val embeddeds: SetProperty<FileSystemLocation> = project.objects.setProperty(FileSystemLocation::class.java)
        .apply(HasConfigurableValue::finalizeValueOnRead)

    fun export(vararg packageName: String) {
        packages.addAll(*packageName)
    }

    fun export(packageName: Provider<String>) {
        packages.add(packageName)
    }

    fun exportAll(packageNames: Provider<out Iterable<String>>) {
        packages.addAll(packageNames)
    }

    fun embed(files: Provider<Set<FileSystemLocation>>) {
        embeddeds.addAll(files)
    }

    @get:Input
    val autoExport: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    @get:Internal
    val exports: Provider<String> = packages.map { names ->
        if (names.isNotEmpty()){
            "-exportcontents:${names.joinToString(",")}"
        } else {
            ""
        }
    }

    @get:Internal
    val embeddedJars: Provider<String> = embeddeds.map(::declareEmbeddedJars)

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
