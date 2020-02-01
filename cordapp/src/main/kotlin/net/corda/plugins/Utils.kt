package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.max

/**
 * Mimics the "project.ext" functionality in groovy which provides a direct
 * accessor to the "ext" extension (See: ExtraPropertiesExtension)
 */

fun <T : Any> Project.findRootProperty(name: String): T? {
    return rootProject.findProperty(name) as? T
}

fun Project.configuration(name: String): Configuration = configurations.single { it.name == name }

class Utils {
    companion object {
        fun createChildConfiguration(name: String, parent: Configuration, configurations: ConfigurationContainer): Configuration {
            return configurations.findByName(name) ?: run {
                val configuration = configurations.create(name) {
                    it.isTransitive = false
                }
                parent.extendsFrom(configuration)
                configuration
            }
        }

        fun createCompileConfiguration(name: String, configurations: ConfigurationContainer): Configuration {
            return createChildConfiguration(name, configurations.single { it.name == "compile" }, configurations)
        }

        fun createRuntimeConfiguration(name: String, configurations: ConfigurationContainer): Configuration {
            return createChildConfiguration(name, configurations.single { it.name == "runtime" }, configurations)
        }

        @JvmStatic
        fun compareVersions(v1: String, v2: String): Int {
            fun parseVersionString(v: String) = v.split(".").flatMap { it.split("-") }.map {
                try {
                    Integer.valueOf(it)!!
                } catch (e: NumberFormatException) {
                    -1
                }
            }

            val parsed1 = parseVersionString(v1)
            val parsed2 = parseVersionString(v2)
            for (i in 0 until max(parsed1.count(), parsed2.count())) {
                val compareResult = parsed1.getOrElse(i) { 0 }.compareTo(parsed2.getOrElse(i) { 0 })
                if (compareResult != 0) {
                    return compareResult
                }
            }
            return 0
        }

        @JvmStatic
        fun createTempFileFromResource(resourcePath: String, tempFileName: String, tempFileExtension: String): Path {
            val path = Files.createTempFile(tempFileName, tempFileExtension)
            this::class.java.classLoader.getResourceAsStream(resourcePath).use {
                Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
            }
            path.toFile().deleteOnExit()
            return path
        }
    }
}
