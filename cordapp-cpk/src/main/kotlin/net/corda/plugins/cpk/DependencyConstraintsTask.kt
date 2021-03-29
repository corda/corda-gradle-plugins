package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DependencyConstraintsTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        private const val DEPENDENCY_CONSTRAINTS = "META-INF/DependencyConstraints"
        private const val CORDAPP_HASH_ALGORITHM = "SHA-256"
        private const val DELIMITER = ','
        private const val CRLF = "\r\n"
        private const val EOF = -1
    }

    init {
        description = "Computes the constraints for this CorDapp's library dependencies."
        group = GROUP_NAME
    }

    private val _libraries: ConfigurableFileCollection = objects.fileCollection()
    val libraries: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _libraries

    @get:Input
    val algorithm: Property<String> = objects.property(String::class.java).convention(CORDAPP_HASH_ALGORITHM)

    @get:Internal
    val constraintsDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val constraintsOutput: Provider<RegularFile> = constraintsDir.file(DEPENDENCY_CONSTRAINTS)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [DependencyConstraintsTask] by accident.
     */
    internal fun setLibrariesFrom(task: TaskProvider<DependencyCalculator>) {
        _libraries.setFrom(task.flatMap(DependencyCalculator::libraries))
        _libraries.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun generate() {
        val algorithmName = algorithm.get().toUpperCase()
        val digest = try {
            MessageDigest.getInstance(algorithmName)
        } catch (_ : NoSuchAlgorithmException) {
            throw InvalidUserDataException("Hash algorithm $algorithmName not available")
        }

        try {
            val encoder = Base64.getEncoder()
            constraintsOutput.get().asFile.bufferedWriter().use { output ->
                libraries.forEach { library ->
                    logger.info("CorDapp library dependency: {}", library.name)
                    output.append(library.name.replace(DELIMITER, '_')).append(DELIMITER)
                        .append(algorithmName).append(DELIMITER)
                        .append(encoder.encodeToString(digest.hashFor(library)))
                        .append(CRLF)
                }
            }
        } catch (e: IOException) {
            throw InvalidUserDataException(e.message ?: "", e)
        }
    }

    /**
     * For computing file hashes.
     */
    private fun MessageDigest.hashFor(file: File): ByteArray {
        file.inputStream().use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length = it.read(buffer)
                if (length == EOF) {
                    break
                }
                update(buffer, 0, length)
            }
        }
        return digest()
    }
}
