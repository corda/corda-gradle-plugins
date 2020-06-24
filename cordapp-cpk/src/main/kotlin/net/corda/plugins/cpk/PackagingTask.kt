package net.corda.plugins.cpk

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class PackagingTask @Inject constructor(objects: ObjectFactory) : Jar() {
    private companion object {
        private const val CORDAPP_CLASSIFIER = "cordapp"
        private const val CORDAPP_EXTENSION = "cpk"
    }

    private val _dependencies: ConfigurableFileCollection = objects.fileCollection()
    val dependencies: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _dependencies

    /**
     * Don't eagerly configure the [DependencyConstraintsTask] task, even
     * if someone eagerly configures this [PackagingTask] by accident.
     */
    internal fun setDependenciesFrom(task: TaskProvider<DependencyConstraintsTask>) {
        // This should also automatically make us depend on task.
        _dependencies.setFrom(task.map(DependencyConstraintsTask::dependencies))
        _dependencies.disallowChanges()
    }

    @get:PathSensitive(RELATIVE)
    @get:InputFile
    val cordapp: RegularFileProperty = objects.fileProperty()

    fun cordapp(item: Any?) {
        when(item) {
            is Jar -> {
                cordapp.set(item.archiveFile)
            }
            is Task -> {
                dependsOn(item)
                cordapp.set(item.outputs.files::getSingleFile)
            }
            is TaskProvider<*> -> {
                dependsOn(item)
                cordapp.set(item.map { RegularFile(it.outputs.files::getSingleFile)})
            }
            else -> {
                throw InvalidUserCodeException("cordapp() requires a task that creates a Jar.")
            }
        }
    }

    init {
        description = "Builds the CorDapp CPK package."
        group = GROUP_NAME

        mainSpec.from(cordapp) { spec ->
            spec.from(dependencies) { deps ->
                deps.into("lib")
            }
        }

        archiveExtension.set(CORDAPP_EXTENSION)
        archiveClassifier.set(CORDAPP_CLASSIFIER)
        fileMode = Integer.parseInt("444", 8)
        dirMode = Integer.parseInt("555", 8)
        manifestContentCharset = "UTF-8"
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        entryCompression = DEFLATED
        metadataCharset = "UTF-8"
        includeEmptyDirs = false
        isCaseSensitive = true
        isZip64 = true
    }
}