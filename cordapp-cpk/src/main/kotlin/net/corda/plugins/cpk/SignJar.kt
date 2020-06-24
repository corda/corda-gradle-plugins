package net.corda.plugins.cpk

import net.corda.plugins.cpk.signing.SigningOptions
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.inject.Inject

@Suppress("Unused", "UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class SignJar @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    companion object {
        private fun createTempFileFromResource(resourcePath: String, tempFileName: String, tempFileExtension: String): Path {
            val path = Files.createTempFile(tempFileName, tempFileExtension)
            this::class.java.classLoader.getResourceAsStream(resourcePath)?.use {
                Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
            }
            return path
        }

        fun sign(task: Task, signing: Signing, file: File) {
            val options = signing.options.toSignJarOptionsMap()
            if (signing.options.hasDefaultOptions()) {
                task.logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStorePath = createTempFileFromResource(SigningOptions.DEFAULT_KEYSTORE, SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION)
                options[SigningOptions.Key.KEYSTORE] = keyStorePath.toString()
            }

            val path = file.toPath()
            options[SigningOptions.Key.JAR] = path.toString()

            try {
                task.logger.info("Jar signing with following options: $options")
                task.ant.invokeMethod("signjar", options)
            } catch (e: Exception) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw InvalidUserDataException("Exception while signing ${path.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (task.logger.isInfoEnabled || task.logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            } finally {
                if (signing.options.hasDefaultOptions()) {
                    Files.delete(Paths.get(options[SigningOptions.Key.KEYSTORE]))
                }
            }
        }
    }

    init {
        description = "Signs the given jars using the configuration from cordapp.signing.options."
        group = "Cordapp"
    }

    private val signing: Signing = project.extensions.getByType(CordappExtension::class.java).signing

    @get:Input
    val postfix: Property<String> = objects.property(String::class.java).convention("-signed")

    private val _inputJars: ConfigurableFileCollection = objects.fileCollection()
    val inputJars: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        @SkipWhenEmpty
        get() = _inputJars

    fun setInputJars(jars: Any?) {
        _inputJars.setFrom(jars ?: return)
    }

    fun inputJars(jars: Any?) = setInputJars(jars)

    private val _outputJars: ConfigurableFileCollection = objects.fileCollection().from(
        _inputJars.elements.map { files ->
            files.map(::toSigned)
        }
    )
    val outputJars: FileCollection
        @OutputFiles
        get() {
            // Don't compute these values more than once.
            // Replace with finalizeValueOnRead() immediately after
            // construction when we upgrade this plugin to Gradle 6.1.
            _outputJars.finalizeValue()
            return _outputJars
        }

    private fun toSigned(file: File): Provider<File> {
        val path = file.absolutePath
        val lastDot = path.lastIndexOf('.')
        return postfix.map { post ->
            File(path.substring(0, lastDot) + post + path.substring(lastDot))
        }
    }

    private fun toSigned(file: FileSystemLocation): Provider<File> = toSigned(file.asFile)

    @TaskAction
    fun build() {
        for (file: File in inputJars) {
            val signedFile = toSigned(file).get()
            Files.copy(file.toPath(), signedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            sign(this, signing, signedFile)
        }
    }
}