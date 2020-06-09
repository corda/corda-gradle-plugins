package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.StringJoiner

@Suppress("UnstableApiUsage")
open class CordappDependencyTask : DefaultTask() {
    private companion object {
        const val CRLF = "\r\n"
    }

    init {
        description = "Computes the hashes of this CorDapp's explicit CorDapp dependencies."
        group = "Cordapp"
    }

    @get:PathSensitive(RELATIVE)
    @get:InputFiles
    val dependencies: FileCollection = project.configuration("cordapp")

    @get:Input
    val algorithm: Property<String> = project.objects.property(String::class.java).convention("SHA-256")

    @get:OutputFile
    val dependencyOutput: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val digest = try {
            MessageDigest.getInstance(algorithm.get())
        } catch (_ : NoSuchAlgorithmException) {
            throw InvalidUserDataException("Hash algorithm ${algorithm.get()} not available")
        }

        try {
            dependencyOutput.get().asFile.bufferedWriter().use { output ->
                dependencies.map { file ->
                    output.append(digest.hashFor(file).toHexString()).append(CRLF)
                }
            }
        } catch (e: IOException) {
            throw InvalidUserCodeException(e.message ?: "", e)
        }
    }

    private fun MessageDigest.hashFor(file: File): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        file.inputStream().use {
            while (true) {
                val length = it.read(buffer)
                if (length == -1) {
                    break
                }
                update(buffer, 0, length)
            }
        }
        return digest()
    }

    private fun ByteArray.toHexString(): String {
        return with(StringJoiner("")) {
            for (b in this@toHexString) {
                add(String.format("%02x", b))
            }
            toString()
        }
    }
}
