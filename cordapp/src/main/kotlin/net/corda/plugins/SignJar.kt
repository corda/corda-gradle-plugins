package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

open class SignJar : DefaultTask() {

    companion object {
        fun sign(project: Project, signing: Signing, file: File, enabled: Boolean = signing.enabled) {
            if (!enabled) {
                project.logger.info("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                return
            }
            val options = signing.options.toSignJarOptionsMap()
            if (signing.options.hasDefaultOptions()) {
                project.logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.")
                val keyStorePath = Utils.createTempFileFromResource(SigningOptions.DEFAULT_KEYSTORE, SigningOptions.DEFAULT_KEYSTORE_FILE, SigningOptions.DEFAULT_KEYSTORE_EXTENSION)
                options["keystore"] = keyStorePath.toString()
            }

            val path = file.toPath()
            options["jar"] = path.toString()

            try {
                project.ant.invokeMethod("signjar", options)
            } catch (e: Exception) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw InvalidUserDataException("Exception while signing ${path.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (project.logger.isInfoEnabled || project.logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            } finally {
                if (signing.options.hasDefaultOptions()) {
                    Paths.get(options["keystore"]).toFile().delete()
                }
            }
        }
    }

    private val signing: Signing = (project.extensions.findByName("cordapp") as CordappExtension).signing

    private var _postfix = "-signed"

    @get:Input
    val postfix: String
        get() = _postfix

    fun setPostfix(value: String) {
        _postfix = value
    }

    fun postfix(value: String) = setPostfix(value)

    private val _inputJars: ConfigurableFileCollection = project.files()

    @get:InputFiles
    @get:SkipWhenEmpty
    val inputJars: FileCollection
        get() = _inputJars

    fun setInputJars(jars: Any?) {
        _inputJars.setFrom(jars ?: return)
    }

    fun inputJars(jars: Any?) = setInputJars(jars)

    @get:OutputFiles
    val outputJars: FileCollection
        get() = project.files(inputJars.map(::toSigned))

    private fun toSigned(file: File): File {
        val path = file.absolutePath
        val lastDot = path.lastIndexOf('.')
        return File(path.substring(0, lastDot) + postfix + path.substring(lastDot))
    }

    @TaskAction
    fun build() {
        if (inputJars.isEmpty) {
            throw InvalidUserDataException("No input JAR file defined, ensure to configure 'inputs property for SignJar task.")
        }
        for (file: File in inputJars) {
            val signedFile = toSigned(file)
            Files.copy(file.toPath(), signedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            sign(project, signing, signedFile, true)
        }
    }
}