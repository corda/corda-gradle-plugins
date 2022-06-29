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
import net.corda.plugins.cpk.copyCpkEnabledTo
import net.corda.plugins.cpk.copyJarEnabledTo
import net.corda.plugins.cpk.isPlatformModule
import net.corda.plugins.cpk.nested
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
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
            .setDescription("Additional CPK dependencies to include inside the CPB.")
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
                        if (cpk is ExternalDependency && cpk.attributes.isEmpty) {
                            cpk.artifact {
                                it.name = dependency.name
                                it.classifier = CPK_ARTIFACT_CLASSIFIER
                                it.type = CPK_FILE_EXTENSION
                            }
                        } else {
                            cpk.attributes(attributor::forCpk)
                        }
                        dependencies.add(cpk)
                    }
            }.apply {
                isCanBeConsumed = false
            }

        project.configurations.create(CORDA_CPB_CONFIGURATION_NAME)
            .attributes(attributor::forCpb)
            .isCanBeResolved = false

        /**
         * We MUST resolve the CPB configurations after every project has been
         * evaluated, but also before Gradle builds its Task Execution Graph!
         *
         * @see [Gradle #17765](https://github.com/gradle/gradle/issues/17765).
         */
        val cpbResolution = project.provider {
            with(cpbPackaging) {
                resolve()
                buildDependencies
            }
        }

        val cpkTask = project.tasks.named(CPK_TASK_NAME, PackagingTask::class.java)
        val cpkPath = cpkTask.flatMap(PackagingTask::getArchiveFile)
        val allCPKs = project.objects.fileCollection().from(cpkPath, cpbPackaging)
        val cpbTaskProvider = project.tasks.register(CPB_TASK_NAME, CpbTask::class.java) { cpbTask ->
            cpbTask.dependsOn(cpbResolution)
            cpbTask.from(allCPKs)
            val cordappExtension = project.extensions.findByType(CordappExtension::class.java)
                ?: throw GradleException("cordapp extension not found")
            cpbTask.inputs.nested("cordappSigning", cordappExtension.signing)

            // Basic configuration of the CPB task.
            cpbTask.destinationDirectory.convention(cpkTask.flatMap(Jar::getDestinationDirectory))
            cpbTask.archiveBaseName.convention(cpkTask.flatMap(Jar::getArchiveBaseName))
            cpbTask.archiveAppendix.convention(cpkTask.flatMap(Jar::getArchiveAppendix))
            cpbTask.archiveVersion.convention(cpkTask.flatMap(Jar::getArchiveVersion))

            cpbTask.doLast {
                if (cordappExtension.signing.enabled.get()) {
                    cpbTask.sign(cordappExtension.signing.options, cpbTask.archiveFile.get().asFile)
                }
            }

            // Disable this task if either the jar task or cpk task is disabled.
            project.gradle.taskGraph.whenReady { graph ->
                copyJarEnabledTo(cpbTask).execute(graph)
                copyCpkEnabledTo(cpbTask).execute(graph)
            }
        }

        with(project.artifacts) {
            add(ARCHIVES_CONFIGURATION, cpbTaskProvider)
            add(CORDA_CPB_CONFIGURATION_NAME, cpbTaskProvider)
        }
    }
}
