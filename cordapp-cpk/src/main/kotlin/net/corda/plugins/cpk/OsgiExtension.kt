package net.corda.plugins.cpk

import org.gradle.api.Project
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar

@Suppress("UnstableApiUsage")
open class OsgiExtension(project: Project, jar: Jar) {
    private val packages: SetProperty<String> = project.objects.setProperty(String::class.java)
        .apply(HasConfigurableValue::finalizeValueOnRead)

    fun add(packageName: String) {
        packages.add(packageName)
    }

    @get:Input
    val autoExport: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    @Suppress("unused")
    @get:Internal
    val exports: Provider<String> = autoExport.flatMap { isAuto ->
        packages.map { names ->
            if (isAuto){
                names.joinToString(",")
            } else {
                ""
            }
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
