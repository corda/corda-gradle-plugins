package net.corda.plugins.cpk

import aQute.bnd.header.OSGiHeader
import aQute.bnd.header.Parameters
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.osgi.framework.Constants.EXPORT_PACKAGE
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject

@Suppress("UnstableApiUsage", "unused")
open class DetectPackagesTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        description = "Scans OSGi metadata in provided jars for required packages."
        group = GROUP_NAME
    }

    @get:Optional
    @get:Input
    val packages: SetProperty<String> = objects.setProperty(String::class.java)

    private val _externalJars: ConfigurableFileCollection = objects.fileCollection()
    val externalJars: FileCollection
        @PathSensitive(RELATIVE)
        @SkipWhenEmpty
        @InputFiles
        get() = _externalJars

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [DetectPackagesTask] by accident.
     */
    internal fun setExternalJarsFrom(task: TaskProvider<DependencyCalculator>) {
        _externalJars.setFrom(task.flatMap(DependencyCalculator::externalJars))
        _externalJars.disallowChanges()
        dependsOn(task)
    }

    private val _detections = objects.setProperty(String::class.java)
    val detections: Provider<out Set<String>>
        @Internal
        get() = _detections

    @TaskAction
    fun analyse() {
        if (packages.isPresent) {
            val requiredPackageNames = packages.get().map(String::trim)
            for (jar in _externalJars) {
                logger.debug("Scanning external jar {}", jar)
                jar.exportPackages.keys.also { pkgs ->
                    pkgs.retainAll(requiredPackageNames)
                    _detections.addAll(pkgs)
                }
            }

            if (logger.isDebugEnabled) {
                _detections.get().forEach { detected ->
                    logger.debug("Detected package {}", detected)
                }
            }
        }
    }

    private val File.exportPackages: Parameters get() {
        val manifest = JarFile(this).use(JarFile::getManifest)
        return OSGiHeader.parseHeader(manifest.mainAttributes.getValue(EXPORT_PACKAGE))
    }
}
