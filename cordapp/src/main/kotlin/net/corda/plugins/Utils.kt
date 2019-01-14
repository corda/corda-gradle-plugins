package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.max

/**
 * Mimics the "project.ext" functionality in groovy which provides a direct
 * accessor to the "ext" extension (See: ExtraPropertiesExtension)
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Project.ext(name: String): T = (extensions.findByName("ext") as ExtraPropertiesExtension).get(name) as T
fun Project.configuration(name: String): Configuration = configurations.single { it.name == name }

class Utils {
    companion object {
        fun createCompileConfiguration(name: String, project: Project) {
            if (project.configurations.none { it.name == name }) {
                val configuration = project.configurations.create(name) {
                    it.isTransitive = false
                }
                project.configurations.single { it.name == "compile" }.extendsFrom(configuration)
            }
        }

        // This function is called from the groovy quasar-utils plugin.
        @JvmStatic
        fun createRuntimeConfiguration(name: String, project: Project) {
            if (project.configurations.none { it.name == name }) {
                val configuration = project.configurations.create(name) {
                    it.isTransitive = false
                }
                project.configurations.single { it.name == "runtime" }.extendsFrom(configuration)
            }
        }

        @JvmStatic
        fun compareVersions(v1 : String, v2 : String) : Int {
            fun parseVersionString(v : String) = v.split(".").flatMap { it.split("-") }.map {
                try {
                    Integer.valueOf(it)!!
                } catch (e : NumberFormatException) {
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