package net.corda.plugins.cpk

import net.corda.plugins.cpk.signing.SigningOptions
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
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
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject

@Suppress("Unused", "UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class SignJar @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    companion object {
        private const val DUMMY_VALUE = "****"

        @Suppress("SameParameterValue")
        private fun writeResourceToFile(resourcePath: String, path: Path) {
            this::class.java.classLoader.getResourceAsStream(resourcePath)?.use {
                Files.copy(it, path, REPLACE_EXISTING)
            }
        }

        fun Task.sign(signing: SigningOptions, file: File) {
            val options = signing.signJarOptions.get()
            val useDefaultKeyStore = !signing.keyStore.isPresent
            if (useDefaultKeyStore) {
                logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStore = File.createTempFile(SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION, temporaryDir).toPath()
                writeResourceToFile(SigningOptions.DEFAULT_KEYSTORE, keyStore)
                options[SigningOptions.Key.KEYSTORE] = keyStore.toString()
            }

            val path = file.toPath()
            options[SigningOptions.Key.JAR] = path.toString()

            logger.info("Jar signing with following options: ${options.toSanitized()}")
            try {
                ant.invokeMethod("signjar", options)
            } catch (e: Exception) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw InvalidUserDataException("Exception while signing ${path.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (logger.isInfoEnabled || logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            } finally {
                if (useDefaultKeyStore) {
                    options[SigningOptions.Key.KEYSTORE]?.also { jarFile ->
                        Files.deleteIfExists(Paths.get(jarFile))
                    }
                }
            }
        }

        private fun Map<String, String>.toSanitized(): Map<String, String> {
            return LinkedHashMap(this).also {
                it.computeIfPresent(SigningOptions.Key.KEYPASS) { _, _ -> DUMMY_VALUE }
                it.computeIfPresent(SigningOptions.Key.STOREPASS) { _, _ -> DUMMY_VALUE }
            }
        }
    }

    private val signing: Signing = project.extensions.findByType(CordappExtension::class.java)?.signing
            ?: throw GradleException("Please apply cordapp-cpk plugin to create cordapp DSL extension.")

    init {
        description = "Signs the given jars using the configuration from cordapp.signing.options."
        group = CORDAPP_TASK_GROUP
        inputs.nested("signing", signing)
    }

    @get:Input
    val postfix: Property<String> = objects.property(String::class.java).convention("-signed")

    private val _inputJars = objects.fileCollection()
    val inputJars: FileCollection
        @PathSensitive(RELATIVE)
        @SkipWhenEmpty
        @InputFiles
        get() = _inputJars

    fun setInputJars(vararg jars: Any) {
        _inputJars.setFrom(*jars)
    }

    fun inputJars(vararg jars: Any) {
        _inputJars.setFrom(*jars)
    }

    private val _outputJars = objects.fileCollection().apply {
        setFrom(_inputJars.elements.map { files -> files.map(::toSigned) })
        disallowChanges()
    }

    val outputJars: FileCollection
        @OutputFiles
        get() = _outputJars

    private fun toSigned(file: FileSystemLocation): Provider<File> = toSigned(file.asFile)

    private fun toSigned(file: File): Provider<File> {
        val path = file.absolutePath
        val lastDot = path.lastIndexOf('.')
        return postfix.map { pfx ->
            File(path.substring(0, lastDot) + pfx + path.substring(lastDot))
        }
    }

    @TaskAction
    fun build() {
        for (file: File in inputJars) {
            val signedFile = toSigned(file).get()
            Files.copy(file.toPath(), signedFile.toPath(), REPLACE_EXISTING)
            sign(signing.options, signedFile)
        }
    }
}
