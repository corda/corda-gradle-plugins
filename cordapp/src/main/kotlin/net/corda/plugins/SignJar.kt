package net.corda.plugins

import net.corda.plugins.cordapp.signing.SigningOptions
import net.corda.plugins.cordapp.signing.SigningOptions.Key
import org.gradle.api.Action
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
import org.gradle.api.tasks.Nested
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
            val opts = signing.options

            val command = mutableListOf<String>()

            // executable
            opts.executable.orNull?.let { command.add(it.asFile.absolutePath) } ?: command.add("jarsigner")

            // keystore
            val useDefaultKeyStore = !opts.keyStore.isPresent
            val keyStorePath: String = if (useDefaultKeyStore) {
                logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStore = File.createTempFile(SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION, temporaryDir).toPath()
                writeResourceToFile(SigningOptions.DEFAULT_KEYSTORE, keyStore)
                keyStore.toString()
            } else {
                opts.keyStore.get().toString()
            }

            command.add("-keystore")
            command.add(keyStorePath)

            // storepass
            command.add("-storepass")
            command.add(opts.storePassword.get())

            // keypass
            command.add("-keypass")
            command.add(opts.keyPassword.get())

            // storetype
            opts.storeType.orNull?.let {
                command.add("-storetype")
                command.add(it)
            }

            // alias
            command.add("-alias")
            command.add(opts.alias.get())

            // sigfile
            opts.signatureFileName.orNull?.let {
                command.add("-sigfile")
                command.add(it)
            }

            // verbose
            if (opts.verbose.get()) command.add("-verbose")

            // strict
            if (opts.strict.get()) command.add("-strict")

            // internalsf
            if (opts.internalSF.get()) command.add("-internalsf")

            // sectionsonly
            if (opts.sectionsOnly.get()) command.add("-sectionsonly")

            // tsa
            opts.tsaUrl.orNull?.let {
                command.add("-tsa")
                command.add(it.toString())
            }

            // tsacert
            opts.tsaCert.orNull?.let {
                command.add("-tsacert")
                command.add(it)
            }

            // sigalg
            opts.signatureAlgorithm.orNull?.let {
                command.add("-sigalg")
                command.add(it)
            }

            // digestalg
            opts.digestAlgorithm.orNull?.let {
                command.add("-digestalg")
                command.add(it)
            }

            // tsadigestalg
            opts.tsaDigestAlgorithm.orNull?.let {
                command.add("-tsadigestalg")
                command.add(it)
            }

            // providerClassPath
            opts.providerClassPath.orNull?.let {
                command.add("-providerClassPath")
                command.add(it)
            }

            // signedjar
            if (outputFile != null) {
                command.add("-signedjar")
                command.add(outputFile.absolutePath)
            }

            // jar
            command.add(file.absolutePath)

            // Log command with passwords masked
            val sanitizedCommand = command.joinToString(" ") { arg ->
                if (arg == opts.storePassword.get() || arg == opts.keyPassword.get()) DUMMY_VALUE else arg
            }
            logger.info("Jar signing with command: jarsigner {}", sanitizedCommand.substringAfter("jarsigner "))

            try {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw InvalidUserDataException("Exception while signing ${file.name}, jarsigner failed with exit code $exitCode. Output: $output")
                }
                if (opts.verbose.get()) {
                    logger.info("jarsigner output: $output")
                }
            } finally {
                if (useDefaultKeyStore) {
                    Files.deleteIfExists(Paths.get(keyStorePath))
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

    private val defaultSigning: Signing = (project.extensions.findByName("cordapp") as CordappExtension).signing

    @get:Nested
    val signing: Signing = objects.newInstance(Signing::class.java).apply {
        options.values(defaultSigning.options)
    }

    fun signing(action: Action<in Signing>) {
        action.execute(signing)
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
