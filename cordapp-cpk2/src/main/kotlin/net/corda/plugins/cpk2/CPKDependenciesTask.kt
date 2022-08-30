package net.corda.plugins.cpk2

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.security.MessageDigest
import java.util.Base64
import java.util.jar.JarFile
import javax.inject.Inject

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
@DisableCachingByDefault
open class CPKDependenciesTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        private const val CPK_DEPENDENCIES_FORMAT_VERSION2 = "2.0"
    }

    init {
        description = "Records this CorDapp's CPK dependencies."
        group = CORDAPP_TASK_GROUP
    }

    @get:Input
    val hashAlgorithm: Property<String> = objects.property(String::class.java)

    private val _projectCpks: ConfigurableFileCollection = objects.fileCollection()
    val projectCpks: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _projectCpks

    private val _remoteCpks: ConfigurableFileCollection = objects.fileCollection()
    val remoteCpks: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _remoteCpks

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val cpkOutput: Provider<RegularFile> = outputDir.file(CPK_DEPENDENCIES)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [CPKDependenciesTask] by accident.
     */
    internal fun setCPKsFrom(task: TaskProvider<DependencyCalculator>) {
        _projectCpks.setFrom(task.flatMap(DependencyCalculator::projectCordapps))
        _projectCpks.disallowChanges()
        _remoteCpks.setFrom(task.flatMap(DependencyCalculator::remoteCordapps))
        _remoteCpks.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun generate() {
        val digest = digestFor(hashAlgorithm.get().toUpperCase())

        try {
            // Write CPK dependency information as JSON document.
            cpkOutput.get().asFile.printWriter().use { pw ->
                JsonDependencyWriter(pw, digest).use { writer ->
                    projectCpks.forEach { cpk ->
                        logger.info("Project CorDapp CPK dependency: {}", cpk.name)
                        writer.writeProjectDependency(cpk)
                    }

                    remoteCpks.forEach { cpk ->
                        logger.info("Remote CorDapp CPK dependency: {}", cpk.name)
                        writer.writeRemoteDependency(cpk)
                    }
                }
            }
        } catch (e: Exception) {
            throw (e as? RuntimeException) ?: InvalidUserDataException(e.message ?: "", e)
        }
    }

    private class JsonDependencyWriter(
        private val output: PrintWriter,
        private val digest: MessageDigest
    ): AutoCloseable {
        private val encoder = Base64.getEncoder()
        private var firstElement = true

        init {
            output.print("{\"formatVersion\":\"$CPK_DEPENDENCIES_FORMAT_VERSION2\",\"dependencies\":[")
        }

        // Escape slashes and quotes inside string
        private fun escapeString(s: String): String = s
            .replace("\\", "\\\\")  // \ replaced with \\
            .replace("\"", "\\\"")  // " replaced with \"

        @Throws(IOException::class)
        private fun writeCommonElements(jar: JarFile) {
            val mainAttributes = jar.manifest.mainAttributes
            output.print("\"name\":\"")
            output.print(escapeString(mainAttributes.getValue(BUNDLE_SYMBOLICNAME)))
            output.print("\",\"version\":\"")
            output.print(escapeString(mainAttributes.getValue(BUNDLE_VERSION)))
            output.print("\",")
            mainAttributes.getValue(CORDA_CPK_TYPE)?.also { cpkType ->
                output.print("\"type\":\"")
                output.print(escapeString(cpkType))
                output.print("\",")
            }
        }

        @Throws(IOException::class)
        fun writeProjectDependency(jar: File) {
            openDependency()
            JarFile(jar).use(::writeCommonElements)
            output.print("\"verifySameSignerAsMe\":true")
            closeDependency()
        }

        @Throws(IOException::class)
        fun writeRemoteDependency(jar: File) {
            openDependency()
            JarFile(jar).use(::writeCommonElements)
            val hash = jar.inputStream().use(digest::hashFor)
            output.print("\"verifyFileHash\":{\"algorithm\":\"")
            output.print(digest.algorithm)
            output.print("\",\"fileHash\":\"")
            output.print(encoder.encodeToString(hash))
            output.print("\"}")
            closeDependency()
        }

        private fun openDependency() {
            if (firstElement) {
                firstElement = false
            } else {
                output.print(',')
            }
            output.print('{')
        }

        private fun closeDependency() = output.print('}')

        override fun close() = output.print("]}")
    }
}
