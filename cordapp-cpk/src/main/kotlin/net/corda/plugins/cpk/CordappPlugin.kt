package net.corda.plugins.cpk

import net.corda.plugins.cpk.SignJar.Companion.sign
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.file.ProjectLayout
import org.gradle.api.java.archives.Attributes
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import javax.inject.Inject

/**
 * Generate a new CPK format CorDapp for use in Corda.
 */
@Suppress("Unused", "UnstableApiUsage")
class CordappPlugin @Inject constructor(private val layouts: ProjectLayout): Plugin<Project> {
    private companion object {
        private const val DEPENDENCY_CONSTRAINTS_TASK_NAME = "cordappDependencyConstraints"
        private const val CORDAPP_EXTENSION_NAME = "cordapp"
        private const val MIN_GRADLE_VERSION = "5.6"
        private const val CPK_TASK_NAME = "cpk"
        private const val UNKNOWN = "Unknown"
        private const val VERSION_X = 999
    }

    /**
     * CorDapp's information.
     */
    private lateinit var cordapp: CordappExtension

    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        if (compareVersions(project.gradle.gradleVersion, MIN_GRADLE_VERSION) < 0) {
            throw GradleException("Gradle versionId ${project.gradle.gradleVersion} is below the supported minimum versionId $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "implementation", "compileOnly" and "runtimeOnly" configurations.
        project.pluginManager.apply(JavaPlugin::class.java)
        project.configurations.apply {
            createCompileConfiguration(CORDAPP_CONFIGURATION_NAME)
            createRuntimeConfiguration(CORDA_RUNTIME_CONFIGURATION_NAME)
            createCompileOnlyConfiguration(CORDA_IMPLEMENTATION_CONFIGURATION_NAME)

            // We need to resolve the contents of our CPK file based on both
            // the runtimeClasspath and cordaImplementation configurations.
            // This won't happen by default because cordaImplementation is a
            // "compile only" configuration.
            @Suppress("UsePropertyAccessSyntax")
            create(CORDAPP_PACKAGING_CONFIGURATION_NAME)
                .extendsFrom(getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME))
                .extendsFrom(getByName(CORDA_IMPLEMENTATION_CONFIGURATION_NAME))
                .setVisible(false)
        }

        cordapp = project.extensions.create(CORDAPP_EXTENSION_NAME, CordappExtension::class.java)
        configureCordappTasks(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its SHA-256 hash is stable!
     */
    private fun configureCordappTasks(project: Project) {
        val jarTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java) { task ->
            val defaultName = task.archiveBaseName.map { baseName -> "${project.group}.$baseName" }
            task.doFirst { t ->
                t as Jar
                t.fileMode = Integer.parseInt("444", 8)
                t.dirMode = Integer.parseInt("555", 8)
                t.manifestContentCharset = "UTF-8"
                t.isPreserveFileTimestamps = false
                t.isReproducibleFileOrder = true
                t.entryCompression = DEFLATED
                t.includeEmptyDirs = false
                t.isCaseSensitive = true
                t.isZip64 = true

                val attributes = t.manifest.attributes
                // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
                if (cordapp.contract.isEmpty() && cordapp.workflow.isEmpty()) {
                    throw InvalidUserDataException("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
                }
                configureCordappAttributes(defaultName.get(), attributes)
            }.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    sign(t, cordapp.signing, (t as Jar).archiveFile.get().asFile)
                } else {
                    t.logger.lifecycle("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                }
            }
        }

        /**
         * Generate an extra resource file containing constraints for all of this CorDapp's dependencies.
         */
        val constraintsDir = layouts.buildDirectory.dir("generated-constraints")
        val constraintsTask = project.tasks.register(DEPENDENCY_CONSTRAINTS_TASK_NAME, DependencyConstraintsTask::class.java) { task ->
            task.dependsOnConstraints()
            task.constraintsDir.set(constraintsDir)
        }
        val sourceSets = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets
        sourceSets.getByName("main") { main ->
            main.output.dir(mapOf("builtBy" to constraintsTask), constraintsDir)
        }

        /**
         * Package the CorDapp and all of its dependencies into a Jar archive with CPK extension.
         * The CPK artifact should have the same base-name and appendix as the CorDapp's [Jar] task.
         */
        val cpkTask = project.tasks.register(CPK_TASK_NAME, PackagingTask::class.java) { task ->
            // Basic configuration of the packaging task.
            task.destinationDirectory.set(jarTask.flatMap(Jar::getDestinationDirectory))
            task.archiveBaseName.set(jarTask.flatMap(Jar::getArchiveBaseName))
            task.archiveAppendix.set(jarTask.flatMap(Jar::getArchiveAppendix))

            // Configure the CPK archive contents.
            task.setDependenciesFrom(constraintsTask)
            task.cordapp.set(jarTask.flatMap(Jar::getArchiveFile))
            task.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    sign(t, cordapp.signing, (t as PackagingTask).archiveFile.get().asFile)
                }
            }
        }
        project.artifacts.add(ARCHIVES_CONFIGURATION, cpkTask)
    }

    private fun configureCordappAttributes(defaultName: String, attributes: Attributes) {
        if (!cordapp.contract.isEmpty()) {
            attributes["Cordapp-Contract-Name"] = cordapp.contract.name.getOrElse(defaultName)
            attributes["Cordapp-Contract-Version"] = checkCorDappVersionId(cordapp.contract.versionId)
            attributes["Cordapp-Contract-Vendor"] = cordapp.contract.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Contract-Licence"] = cordapp.contract.licence.getOrElse(UNKNOWN)
        }
        if (!cordapp.workflow.isEmpty()) {
            attributes["Cordapp-Workflow-Name"] = cordapp.workflow.name.getOrElse(defaultName)
            attributes["Cordapp-Workflow-Version"] = checkCorDappVersionId(cordapp.workflow.versionId)
            attributes["Cordapp-Workflow-Vendor"] = cordapp.workflow.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Workflow-Licence"] = cordapp.workflow.licence.getOrElse(UNKNOWN)
        }

        val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
        attributes["Target-Platform-Version"] = targetPlatformVersion
        attributes["Min-Platform-Version"] = minimumPlatformVersion
        if (cordapp.sealing.enabled.get()) {
            attributes["Sealed"] = java.lang.Boolean.TRUE
        }
    }

    private fun checkPlatformVersionInfo(): Pair<Int, Int> {
        // If the minimum platform version is not set, default to X.
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion.getOrElse(VERSION_X)
        val targetPlatformVersion = cordapp.targetPlatformVersion.orNull
                ?: throw InvalidUserDataException("CorDapp `targetPlatformVersion` was not specified in the `cordapp` metadata section.")
        if (targetPlatformVersion < VERSION_X) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than $VERSION_X.")
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than the `minimumPlatformVersion` ($minimumPlatformVersion)")
        }
        return Pair(targetPlatformVersion, minimumPlatformVersion)
    }

    private fun checkCorDappVersionId(versionId: Property<Int>): Int {
        if (!versionId.isPresent)
            throw InvalidUserDataException("CorDapp `versionId` was not specified in the associated `contract` or `workflow` metadata section. Please specify a whole number starting from 1.")
        val value = versionId.get()
        if (value < 1) {
            throw InvalidUserDataException("CorDapp `versionId` must not be smaller than 1.")
        }
        return value
    }
}