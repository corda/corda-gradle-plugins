package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtraPropertiesExtension

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
    }

}
