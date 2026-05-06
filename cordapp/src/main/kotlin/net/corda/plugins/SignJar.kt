package net.corda.plugins

import net.corda.plugins.cordapp.signing.SigningOptions
import net.corda.plugins.cordapp.signing.SigningOptions.Key
import org.gradle.api.DefaultTask
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
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject

@Suppress("UnstableApiUsage", "unused")
@DisableCachingByDefault
open class SignJar @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    companion object {
        private const val DUMMY_VALUE = "****"

        @Suppress("SameParameterValue")
        private fun writeResourceToFile(resourcePath: String, path: Path) {
            this::class.java.classLoader.getResourceAsStream(resourcePath)?.use { input ->
                Files.copy(input, path, REPLACE_EXISTING)
            }
        }

        fun Task.sign(signing: Signing, file: File, outputFile: File? = null) {
            val options = signing.options.signJarOptions.get()
            val jvmArgs = signing.options.jarsignerJvmArgs.orNull ?: emptyList()

            val useDefaultKeyStore = !signing.options.keyStore.isPresent
            if (useDefaultKeyStore) {
                logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStore = File.createTempFile(SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION, temporaryDir).toPath()
                writeResourceToFile(SigningOptions.DEFAULT_KEYSTORE, keyStore)
                options[Key.KEYSTORE] = keyStore.toString()
            }

            val path = file.toPath()
            options[Key.JAR] = path.toString()

            if (outputFile != null) {
                options[Key.SIGNEDJAR] = outputFile.toPath().toString()
            }

            val jvmArgsMessage = if (jvmArgs.isNotEmpty()) " and JVM args: ${jvmArgs.joinToString()}." else "."
            logger.info("Jar signing with following options: ${options.toSanitized().entries.joinToString { "${it.key}=${it.value}" }}$jvmArgsMessage")
            try {
                if (jvmArgs.isEmpty()) {
                    ant.invokeMethod("signjar", options)
                } else {
                    // direct jarsigner execution path
                    val executable = signing.options.executable.orNull?.asFile?.absolutePath ?: "jarsigner"
                    val args = mutableListOf<String>()
                    jvmArgs.forEach{ arg ->
                        args.add("-J$arg")
                    }
                    // convert options map to jarsigner args
                    options.forEach { (key, value) ->
                        when (key) {
                            Key.JAR -> {
                                //handled later
                            }
                            Key.ALIAS -> {
                                // handled later
                            }
                            Key.VERBOSE,
                            Key.STRICT,
                            Key.INTERNALSF,
                            Key.SECTIONSONLY,
                            Key.LAZY,
                            Key.PRESERVELASTMODIFIED,
                            Key.FORCE -> {
                                if (value.toBoolean()) {
                                    args.add("-$key")
                                }
                            }
                            else -> {
                                args.add("-$key")
                                args.add(value)
                            }
                        }
                    }
                    // Required args
                    args.add(options[Key.JAR]!!)
                    args.add(options[Key.ALIAS]!!)

                    project.exec { execSpec ->
                        execSpec.executable = executable
                        execSpec.args = args
                    }
                }
            } catch (e: Exception) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw InvalidUserDataException("Exception while signing ${path.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (logger.isInfoEnabled || logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            } finally {
                if (useDefaultKeyStore) {
                    options[Key.KEYSTORE]?.also { jarFile ->
                        Files.deleteIfExists(Paths.get(jarFile))
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
