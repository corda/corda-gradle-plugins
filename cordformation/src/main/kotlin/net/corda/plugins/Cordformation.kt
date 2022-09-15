package net.corda.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import org.gradle.util.GradleVersion
import java.io.File

/**
 * The Cordformation plugin deploys nodes to a directory in a state ready to be used by a developer for experimentation,
 * testing, and debugging. It will prepopulate several fields in the configuration and create a simple node runner.
 */
@Suppress("UnstableApiUsage")
class Cordformation : Plugin<Project> {
    internal companion object {
        private const val CORDFORMATION_TYPE = "cordformationInternal"
        private const val DEFAULT_JOLOKIA_VERSION = "1.6.2"
        private const val MINIMUM_GRADLE_VERSION = "7.0"

        /**
         * Gets a resource file from this plugin's JAR file by creating an intermediate tmp dir
         *
         * @param filePathInJar The file in the JAR, relative to root, you wish to access.
         * @return A file handle to the file in the JAR.
         */
        fun getPluginFile(task: Task, filePathInJar: String): File {
            val outputFile = File(task.temporaryDir, filePathInJar)
            outputFile.outputStream().use { output ->
                Cordformation::class.java.getResourceAsStream(filePathInJar)?.use { input ->
                    input.copyTo(output)
                }
            }
            return outputFile
        }

        private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
            return flatMapTo(LinkedHashSet()) { dep -> configuration.files(dep) }
        }

        /**
         * Gets a current built corda jar file
         *
         * @param configurations The [ConfigurationContainer] for this project.
         * @param jarExpression A [Regex] to match the name of our jar.
         * @return A [File] for the requested jar artifact.
         */
        fun verifyAndGetRuntimeJar(configurations: ConfigurationContainer, jarExpression: Regex): File {
            val cordaDeps = configurations.getByName(CORDA_CONFIGURATION_NAME).allDependencies
            val maybeJar = cordaDeps.toUniqueFiles(configurations.getByName(DEPLOY_CORDFORMATION_CONFIGURATION_NAME)).filter {
                jarExpression matches it.name
            }
            if (maybeJar.isEmpty()) {
                throw IllegalStateException("No JAR matching '$jarExpression' found. Have you deployed the Corda project to Maven?")
            } else {
                val jar = maybeJar.single()
                require(jar.isFile) { "$jar either does not exist or is not a file" }
                return jar
            }
        }

        val executableFileMode = "0755".toInt(8)
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw GradleException("The cordformation plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }

        project.configurations.apply {
            val cordformation = createBasicConfiguration(CORDFORMATION_TYPE).withDependencies { dependencies ->
                // TODO: improve how we re-use existing declared external variables from root gradle.build
                val jolokiaVersion = project.findRootProperty("jolokia_version") ?: DEFAULT_JOLOKIA_VERSION
                val jolokia = project.dependencies.create("org.jolokia:jolokia-jvm:$jolokiaVersion:agent")
                // The Jolokia agent is a fat jar really, so we don't want its transitive dependencies.
                (jolokia as? ModuleDependency)?.isTransitive = false
                dependencies.add(jolokia)
            }

            val cordaBootstrapper = createBasicConfiguration(CORDA_BOOTSTRAPPER_CONFIGURATION_NAME)
            val cordapp = createBasicConfiguration(CORDAPP_CONFIGURATION_NAME)
            val corda = createBasicConfiguration(CORDA_CONFIGURATION_NAME)

            create(CORDA_DRIVER_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isTransitive = true
                it.isVisible = false
            }
            create(DEPLOY_BOOTSTRAPPER_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isTransitive = true
                it.isVisible = false
                it.extendsFrom(cordaBootstrapper)
            }
            create(DEPLOY_CORDAPP_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isTransitive = false
                it.isVisible = false
                it.extendsFrom(cordapp)
            }
            create(DEPLOY_CORDFORMATION_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isTransitive = false
                it.isVisible = false
                it.extendsFrom(corda, cordformation)
            }

            // Adjust these configurations if this project has also applied the 'java' plugin.
            project.pluginManager.withPlugin("java") {
                getByName(IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(cordapp)
            }
        }
    }
}
