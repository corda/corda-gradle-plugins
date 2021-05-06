package net.corda.plugins.cpk

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class PackagingTask @Inject constructor(objects: ObjectFactory, archiveOps: ArchiveOperations) : Jar() {
    private companion object {
        private const val CORDAPP_CLASSIFIER = "cordapp"
        private const val CORDAPP_EXTENSION = "cpk"
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

    private val cpkManifest: Provider<Manifest> = objects.property(Manifest::class.java)
        .value(cordapp.map { JarFile(it.asFile).use(JarFile::getManifest) })
        .apply(Property<Manifest>::finalizeValueOnRead)

    private fun getAttribute(name: String): Provider<String?> = cpkManifest.map { man ->
        man.mainAttributes.getValue(name)
    }

    init {
        description = "Builds the CorDapp CPK package."
        group = GROUP_NAME

        manifest {
            it.attributes(linkedMapOf(
                CPK_FORMAT_TAG to CPK_FORMAT,
                CPK_CORDAPP_NAME to getAttribute(BUNDLE_SYMBOLICNAME),
                CPK_CORDAPP_VERSION to getAttribute(BUNDLE_VERSION),
                CPK_CORDAPP_LICENCE to getAttribute(BUNDLE_LICENSE),
                CPK_CORDAPP_VENDOR to getAttribute(BUNDLE_VENDOR)
            ))
        }

        mainSpec.from(cordapp).from(libraries) { libs ->
            libs.into("lib")
        }

        /**
         * Extract the CPKDependencies file from the CorDapp "main" jar.
         * Ensure that this file is correctly listed with all the other
         * META-INF artifacts inside the CPK archive, rather than being
         * added after the libraries.
         */
        metaInf.from(project.provider {
            archiveOps.zipTree(cordapp).matching { it.include(CPK_DEPENDENCIES) }
        }) { spec ->
            spec.includeEmptyDirs = false
            spec.eachFile { file ->
                file.path = file.path.removePrefix("META-INF/")
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
        duplicatesStrategy = FAIL
        metadataCharset = "UTF-8"
        includeEmptyDirs = false
        isCaseSensitive = true
        isZip64 = true
    }
}