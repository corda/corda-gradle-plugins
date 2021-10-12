package net.corda.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
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

        /**
         * Gets a current built corda jar file
         *
         * @param project The project environment this plugin executes in.
         * @param jarName The name of the JAR you wish to access.
         * @return A file handle to the file in the JAR.
         */
        fun verifyAndGetRuntimeJar(project: Project, jarName: String): File {
            val releaseVersion = project.findRootProperty("corda_release_version")
                    ?: throw IllegalStateException("Could not find a valid declaration of \"corda_release_version\"")
            // need to cater for optional classifier (eg. corda-4.3-jdk11.jar)
            val pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex()
            val maybeJar = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME).filter {
                it.toString().contains(pattern)
            }
            if (maybeJar.isEmpty) {
                throw IllegalStateException("No $jarName JAR found. Have you deployed the Corda project to Maven? Looked for \"$jarName-$releaseVersion.jar\"")
            } else {
                val jar = maybeJar.singleFile
                require(jar.isFile) { "$jar either does not exist or is not a file" }
                return jar
            }
        }

        val executableFileMode = "0755".toInt(8)
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw GradleException("The Cordformation plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }

        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "compile", "compileOnly" and "runtime" configurations.
        project.pluginManager.apply(JavaPlugin::class.java)

        project.configurations.apply {
            val cordapp = createImplementationConfiguration(CORDAPP_CONFIGURATION_NAME)
            val cordaRuntimeOnly = createRuntimeOnlyConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)

            createChildConfiguration(CORDFORMATION_TYPE, cordaRuntimeOnly).withDependencies { dependencies ->
                // TODO: improve how we re-use existing declared external variables from root gradle.build
                val jolokiaVersion = project.findRootProperty("jolokia_version") ?: DEFAULT_JOLOKIA_VERSION
                val jolokia = project.dependencies.create("org.jolokia:jolokia-jvm:$jolokiaVersion:agent")
                // The Jolokia agent is a fat jar really, so we don't want its transitive dependencies.
                (jolokia as ModuleDependency).isTransitive = false
                dependencies.add(jolokia)
            }

            create(CORDA_DRIVER_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isVisible = false
            }
            create(DEPLOY_CORDAPP_CONFIGURATION_NAME) {
                it.isCanBeConsumed = false
                it.isTransitive = false
                it.isVisible = false
                it.extendsFrom(cordapp)
            }
        }
    }
}
