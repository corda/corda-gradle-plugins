package net.corda.plugins.cpb

import net.corda.plugins.cpk.ALL_CORDAPPS_CONFIGURATION_NAME
import net.corda.plugins.cpk.Attributor
import net.corda.plugins.cpk.CPK_ARTIFACT_CLASSIFIER
import net.corda.plugins.cpk.CPK_FILE_EXTENSION
import net.corda.plugins.cpk.CPK_TASK_NAME
import net.corda.plugins.cpk.CordappExtension
import net.corda.plugins.cpk.CordappPlugin
import net.corda.plugins.cpk.PackagingTask
import net.corda.plugins.cpk.SignJar.Companion.sign
import net.corda.plugins.cpk.isPlatformModule
import net.corda.plugins.cpk.nested
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.tasks.bundling.Jar

@Suppress("Unused", "UnstableApiUsage")
class CpbPlugin : Plugin<Project> {

    companion object {
        private const val CPB_TASK_NAME = "cpb"
        private const val CPB_CONFIGURATION_NAME = CPB_TASK_NAME
        private const val CORDA_CPB_CONFIGURATION_NAME = "cordaCPB"
        private const val CPB_PACKAGING_CONFIGURATION_NAME = "cpbPackaging"
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(CordappPlugin::class.java)
        val allCordappsConfiguration = project.configurations.getByName(ALL_CORDAPPS_CONFIGURATION_NAME)
        val attributor = Attributor(project.objects)

        val cpbConfiguration = project.configurations.create(CPB_CONFIGURATION_NAME)
            .extendsFrom(allCordappsConfiguration)
            .setVisible(false)
            .apply {
                isCanBeConsumed = false
                isCanBeResolved = false
            }

        val cpbPackaging = project.configurations.create(CPB_PACKAGING_CONFIGURATION_NAME)
            .setTransitive(false)
            .setVisible(false)
            .withDependencies { dependencies ->
                // Force Gradle to discover any transitive CPKs
                // and include them in cpbConfiguration.
                project.configurations.detachedConfiguration()
                    .setTransitive(false)
                    .setVisible(false)
                    .extendsFrom(cpbConfiguration)
                    .resolvedConfiguration

                cpbConfiguration.allDependencies
                    .filterIsInstance(ModuleDependency::class.java)
                    .filterNot(::isPlatformModule)
                    .forEach { dependency ->
                        val cpk = dependency.copy()
                        when (dependency) {
                            is ExternalDependency -> {
                                cpk.artifact {
                                    it.name = dependency.name
                                    it.classifier = CPK_ARTIFACT_CLASSIFIER
                                    it.type = CPK_FILE_EXTENSION
                                }
                            }
                            else -> {
                                cpk.attributes(attributor::forCpk)
                            }
                        }
                        dependencies.add(cpk)
                    }
            }.apply {
                isCanBeConsumed = false
            }

        project.configurations.maybeCreate(CORDA_CPB_CONFIGURATION_NAME)
            .attributes(attributor::forCpb)
            .isCanBeResolved = false

        /**
         * @see [Gradle #17765](https://github.com/gradle/gradle/issues/17765).
         */
        project.afterEvaluate {
            // We MUST resolve the CPB configurations before
            // Gradle builds the Task Execution Graph!
            cpbPackaging.resolve()
        }

        val cpkPath = project.tasks.named(CPK_TASK_NAME, PackagingTask::class.java)
            .flatMap(PackagingTask::getArchiveFile)
        val allCPKs = project.objects.fileCollection().from(cpkPath, cpbPackaging)
        val cpbTaskProvider = project.tasks.register(CPB_TASK_NAME, CpbTask::class.java) { cpbTask ->
            cpbTask.from(allCPKs)
            val cordappExtension = project.extensions.findByType(CordappExtension::class.java)
                ?: throw GradleException("Cordapp extension not found")
            cpbTask.inputs.nested("cordappSigning", cordappExtension.signing)

            // Basic configuration of the CPB task.
            val jarTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java)
            cpbTask.destinationDirectory.convention(jarTask.flatMap(Jar::getDestinationDirectory))
            cpbTask.archiveBaseName.convention(jarTask.flatMap(Jar::getArchiveBaseName))
            cpbTask.archiveAppendix.convention(jarTask.flatMap(Jar::getArchiveAppendix))

            cpbTask.doLast {
                if (cordappExtension.signing.enabled.get()) {
                    cpbTask.sign(cordappExtension.signing.options, cpbTask.archiveFile.get().asFile)
                }
            }
        }
        project.artifacts.add(ARCHIVES_CONFIGURATION, cpbTaskProvider)
        project.artifacts.add(CORDA_CPB_CONFIGURATION_NAME, cpbTaskProvider)
    }
}
