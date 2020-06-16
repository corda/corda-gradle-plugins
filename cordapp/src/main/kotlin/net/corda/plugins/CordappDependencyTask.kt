package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLClassLoader
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.StringJoiner
import java.util.function.Consumer

@Suppress("UnstableApiUsage")
open class CordappDependencyTask : DefaultTask() {
    private companion object {
        const val CORDAPP_DEPENDENCIES = "META-INF/Cordapp-Dependencies"
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

    @get:Internal
    val dependencyDir: DirectoryProperty = project.objects.directoryProperty()

    @get:OutputFile
    val dependencyOutput: Provider<RegularFile> = dependencyDir.file(CORDAPP_DEPENDENCIES)

    @TaskAction
    fun generate() {
        val digest = try {
            MessageDigest.getInstance(algorithm.get())
        } catch (_ : NoSuchAlgorithmException) {
            throw InvalidUserDataException("Hash algorithm ${algorithm.get()} not available")
        }

        try {
            dependencyOutput.get().asFile.bufferedWriter().use { output ->
                dependencies.map { cordapp ->
                    output.append(digest.hashFor(cordapp).toHexString()).append(CRLF)
                    cordapp.transitiveDependencies(Consumer { hash ->
                        output.append(hash).append(CRLF)
                    })
                }
            }
        } catch (e: IOException) {
            throw InvalidUserCodeException(e.message ?: "", e)
        }
    }

    private fun File.transitiveDependencies(lineAction: Consumer<String>) {
        return URLClassLoader(arrayOf(toURI().toURL()), null).use { cl ->
            (cl.getResourceAsStream(CORDAPP_DEPENDENCIES) ?: return).bufferedReader().use { br ->
                br.lines().forEach(lineAction)
            }
        }
    }

    private fun MessageDigest.hashFor(file: File): ByteArray = hashFor(file.inputStream())

    private fun MessageDigest.hashFor(input: InputStream): ByteArray {
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
