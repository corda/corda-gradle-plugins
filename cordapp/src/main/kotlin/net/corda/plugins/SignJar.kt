package net.corda.plugins

import net.corda.plugins.cordapp.signing.SigningOptions
import net.corda.plugins.cordapp.signing.SigningOptions.Key
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class SignJar @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    companion object {
        private const val DUMMY_VALUE = "****"

        fun Task.sign(signing: Signing, file: File, outputFile: File? = null) {
            val options = signing.options.toSignJarOptionsMap()
            if (signing.options.hasDefaultOptions()) {
                logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStorePath = CordappUtils.createTempFileFromResource(SigningOptions.DEFAULT_KEYSTORE, SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION)
                options[Key.KEYSTORE] = keyStorePath.toString()
            }

            val path = file.toPath()
            options[Key.JAR] = path.toString()

            if (outputFile != null) {
                options[Key.SIGNEDJAR] = outputFile.toPath().toString()
            }

            try {
                logger.info("Jar signing with following options: ${options.toSanitized()}")
                ant.invokeMethod("signjar", options)
            } catch (e: Exception) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw InvalidUserDataException("Exception while signing ${path.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (logger.isInfoEnabled || logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            } finally {
                if (signing.options.hasDefaultOptions()) {
                    options[Key.KEYSTORE]?.apply {
                        Paths.get(this).toFile().delete()
                    }
                }
            }
        }

        private fun MutableMap<String, String>.toSanitized(): Map<String, String> {
            return toMap(LinkedHashMap()).also {
                it.computeIfPresent(Key.KEYPASS) { _, _ -> DUMMY_VALUE }
                it.computeIfPresent(Key.STOREPASS) { _, _ -> DUMMY_VALUE }
            }
        }
    }

    init {
        description = "Signs the given jars using the configuration from cordapp.signing.options."
        group = CORDAPP_TASK_GROUP
    }

    private val signing: Signing = (project.extensions.findByName("cordapp") as CordappExtension).signing

    @get:Input
    val postfix: Property<String> = objects.property(String::class.java).convention("-signed")

    private val _inputJars = objects.fileCollection()

    val inputJars: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        @SkipWhenEmpty
        get() = _inputJars

    fun setInputJars(jars: Any?) {
        _inputJars.setFrom(jars ?: return)
    }

    fun inputJars(jars: Any?) = setInputJars(jars)

    private val _outputJars = objects.fileCollection().apply {
        setFrom(_inputJars.elements.map { files -> files.map(::toSigned) })
        disallowChanges()
    }

    val outputJars: FileCollection
        @OutputFiles
        get() = _outputJars

    private fun toSigned(file: FileSystemLocation): Provider<File> = toSigned(file.asFile)

    private fun toSigned(file: File): Provider<File> {
        return postfix.map { pfx ->
            File(addSuffix(file.absolutePath, pfx))
        }
    }

    private fun addSuffix(path: String, suffix: String): String {
        return when (val lastDot = path.lastIndexOf('.')) {
            -1 -> path + suffix
            else -> path.substring(0, lastDot) + suffix + path.substring(lastDot)
        }
    }

    @TaskAction
    fun build() {
        for (file: File in inputJars) {
            sign(signing, file, toSigned(file).get())
        }
    }
}
