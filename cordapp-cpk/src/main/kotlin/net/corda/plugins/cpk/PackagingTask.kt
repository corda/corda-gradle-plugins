package net.corda.plugins.cpk

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.work.DisableCachingByDefault
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.util.Collections.unmodifiableList
import javax.inject.Inject

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
@DisableCachingByDefault
open class PackagingTask @Inject constructor(objects: ObjectFactory) : Jar() {
    private companion object {
        private val MANIFEST_MAPPING = unmodifiableList(listOf(
            BUNDLE_SYMBOLICNAME to CPK_CORDAPP_NAME,
            BUNDLE_VERSION to CPK_CORDAPP_VERSION,
            BUNDLE_LICENSE to CPK_CORDAPP_LICENCE,
            BUNDLE_VENDOR to CPK_CORDAPP_VENDOR,
            CORDAPP_PLATFORM_VERSION to CPK_PLATFORM_VERSION
        ))
    }

    private val _libraries: ConfigurableFileCollection = objects.fileCollection()
    val libraries: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _libraries

    /**
     * Don't eagerly configure the [DependencyConstraintsTask] task, even
     * if someone eagerly configures this [PackagingTask] by accident.
     */
    internal fun setLibrariesFrom(task: TaskProvider<DependencyConstraintsTask>) {
        // This should also automatically make us depend on the task.
        _libraries.setFrom(task.map(DependencyConstraintsTask::libraries))
        _libraries.disallowChanges()
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
                cordapp.set(item.map { task -> RegularFile(task.outputs.files::getSingleFile) })
            }
            else -> {
                throw InvalidUserCodeException("cordapp() requires a task that creates a Jar.")
            }
        }
    }

    init {
        description = "Builds the CorDapp CPK package."
        group = CORDAPP_TASK_GROUP

        mainSpec.from(cordapp).from(libraries) { libs ->
            libs.into("lib")
        }

        archiveExtension.set(CPK_FILE_EXTENSION)
        archiveClassifier.set(CPK_ARTIFACT_CLASSIFIER)
        fileMode = Integer.parseInt("444", 8)
        dirMode = Integer.parseInt("555", 8)
        manifestContentCharset = "UTF-8"
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        entryCompression = DEFLATED
        duplicatesStrategy = FAIL
        metadataCharset = "UTF-8"
        includeEmptyDirs = false
        isCaseSensitive = true
        isZip64 = true
    }

    @TaskAction
    override fun copy() {
        /**
         * Any [Task.doFirst] action defined from the [PackagingTask] constructor would
         * actually be executed after [TaskAction], which would obviously be too late.
         * This seems to be a long-standing issue with Gradle, see:
         * - [GRADLE-2064](https://issues.gradle.org/browse/GRADLE-2064)
         * - [#9142](https://github.com/gradle/gradle/issues/9142)
         * Update the CPK manifest as the first step of [TaskAction] instead.
         */
        val jarAttributes = cordapp.asFile.get().manifest.mainAttributes
        val cpkAttributes = linkedMapOf(CPK_FORMAT_TAG to CPK_FORMAT)

        MANIFEST_MAPPING.forEach { mapping ->
            jarAttributes.getValue(mapping.first)?.let { value ->
                cpkAttributes[mapping.second] = value
            }
        }

        manifest.attributes(cpkAttributes)

        super.copy()
    }
}
