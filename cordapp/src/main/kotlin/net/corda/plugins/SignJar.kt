package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

open class SignJar : DefaultTask() {

    companion object {
        fun sign(project: Project, signing: Signing, file: File) {
            if (!signing.enabled) {
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

    @TaskAction
    fun build() {
        val extension = project.extensions.findByName("signing")
        if (extension == null) {
            throw InvalidUserDataException("Can not execute task of type 'SignJar' without applying 'cordapp' plugin.")
        }
        if (this.inputs.files.isEmpty) {
            throw InvalidUserDataException("No input JAR file defined, ensure to configure inputs property for SignJar task.")
        }
        val file = if (!outputs.files.isEmpty) {
            Files.copy(inputs.files.singleFile.toPath(), outputs.files.singleFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            outputs.files.singleFile
        } else {
            inputs.files.singleFile
        }
        val signing = extension as Signing
        signing.enabled(true)
        sign(project, signing, file)
    }
}