package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.io.IOException
import java.util.jar.JarFile
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class CPKDependenciesTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        const val CPK_DEPENDENCIES = "META-INF/CPKDependencies"
        const val DELIMITER = ','
        const val CRLF = "\r\n"
    }

    init {
        description = "Records this CorDapp's CPK dependencies."
        group = GROUP_NAME
    }

    private val _cpks: ConfigurableFileCollection = objects.fileCollection()
    val cpks: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _cpks

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val cpkOutput: Provider<RegularFile> = outputDir.file(CPK_DEPENDENCIES)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [CPKDependenciesTask] by accident.
     */
    internal fun setCPKsFrom(task: TaskProvider<DependencyCalculator>) {
        _cpks.setFrom(task.flatMap(DependencyCalculator::cordapps))
        _cpks.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun generate() {
        try {
            cpkOutput.get().asFile.bufferedWriter().use { output ->
                cpks.forEach { cpk ->
                    logger.info("CorDapp CPK dependency: {}", cpk.name)
                    val mainAttributes = JarFile(cpk).use(JarFile::getManifest).mainAttributes
                    mainAttributes.getValue(BUNDLE_SYMBOLICNAME)?.also { bsn ->
                        val bundleVersion = mainAttributes.getValue(BUNDLE_VERSION) ?: ""
                        output.append(bsn).append(DELIMITER)
                            .append(bundleVersion)
                            .append(CRLF)
                    }
                }
            }
        } catch (e: IOException) {
            throw InvalidUserCodeException(e.message ?: "", e)
        }
    }
}
