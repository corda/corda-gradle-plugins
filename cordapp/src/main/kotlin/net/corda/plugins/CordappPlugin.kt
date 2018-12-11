package net.corda.plugins

import net.corda.plugins.SignJar.Companion.sign
import net.corda.plugins.Utils.Companion.compareVersions
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
@Suppress("UNUSED")
class CordappPlugin : Plugin<Project> {
    private companion object {
        private const val UNKNOWN = "Unknown"
        private const val MIN_GRADLE_VERSION = "4.0"
    }

    /**
     * CorDapp's information.
     */
    lateinit var cordapp: CordappExtension

    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        if (compareVersions(project.gradle.gradleVersion, MIN_GRADLE_VERSION) < 0) {
            throw GradleException("Gradle versionId ${project.gradle.gradleVersion} is below the supported minimum versionId $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)
        Utils.createRuntimeConfiguration("cordaRuntime", project)

        cordapp = project.extensions.create("cordapp", CordappExtension::class.java, project.objects)
        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private fun configureCordappJar(project: Project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        val task = project.task("configureCordappFatJar")
        val jarTask = project.tasks.getByName("jar") as Jar
        jarTask.doFirst {
            val attributes = jarTask.manifest.attributes
            var skip = false
            // Corda 4 attributes support
            if (cordapp.contract.name != null) {
                attributes["Cordapp-Contract-Name"] = cordapp.contract.name ?: "${project.group}.${jarTask.baseName}"
                attributes["Cordapp-Contract-Version"] = parseVersion(cordapp.contract.versionId.toString())
                attributes["Cordapp-Contract-Vendor"] = cordapp.contract.vendor ?: UNKNOWN
                attributes["Cordapp-Contract-Licence"] = cordapp.contract.licence ?: UNKNOWN
                skip = true
            }
            if (cordapp.workflow.name != null) {
                attributes["Cordapp-Workflow-Name"] = cordapp.workflow.name ?: "${project.group}.${jarTask.baseName}"
                attributes["Cordapp-Workflow-Version"] = parseVersion(cordapp.workflow.versionId.toString())
                attributes["Cordapp-Workflow-Vendor"] = cordapp.workflow.vendor ?: UNKNOWN
                attributes["Cordapp-Workflow-Licence"] = cordapp.workflow.licence ?: UNKNOWN
                skip = true
            }
            // Deprecated support (Corda 3)
            if (!skip && cordapp.info.name != null) {
                attributes["Name"] = cordapp.info.name ?: "${project.group}.${jarTask.baseName}"
                attributes["Implementation-Version"] = cordapp.info.version ?: project.version
                attributes["Implementation-Vendor"] = cordapp.info.vendor ?: UNKNOWN
            }
            val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
            attributes["Target-Platform-Version"] = targetPlatformVersion
            attributes["Min-Platform-Version"] = minimumPlatformVersion
            if (cordapp.sealing.enabled) {
                attributes["Sealed"] = "true"
            }
        }.doLast {
            sign(project, cordapp.signing, it.outputs.files.singleFile)
        }
        task.doLast {
            jarTask.from(getDirectNonCordaDependencies(project).map {
                project.logger.info("CorDapp dependency: ${it.name}")
                project.zipTree(it)
            }).apply {
                exclude("META-INF/*.SF")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.RSA")
                exclude("META-INF/*.MF")
                exclude("META-INF/LICENSE")
                exclude("META-INF/NOTICE")
            }
        }
        jarTask.dependsOn(task)
    }

    private fun getDirectNonCordaDependencies(project: Project): Set<File> {
        project.logger.info("Finding direct non-corda dependencies for inclusion in CorDapp JAR")
        val excludes = listOf(
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-stdlib"),
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-stdlib-jre8"),
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-stdlib-jdk8"),
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-reflect"),
                mapOf("group" to "co.paralleluniverse", "name" to "quasar-core")
        )

        val runtimeConfiguration = project.configuration("runtime")
        // The direct dependencies of this project
        val excludeDeps = project.configuration("cordapp").allDependencies +
                project.configuration("cordaCompile").allDependencies +
                project.configuration("cordaRuntime").allDependencies
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        val filteredDeps = directDeps.filter { dep ->
            excludes.none { exclude -> (exclude["group"] == dep.group) && (exclude["name"] == dep.name) }
        }
        filteredDeps.forEach {
            // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
            val group = it.group ?: ""
            if (group.startsWith("net.corda.") || group.startsWith("com.r3.corda.")) {
                project.logger.warn("You appear to have included a Corda platform component ($it) using a 'compile' or 'runtime' dependency." +
                        "This can cause node stability problems. Please use 'corda' instead." +
                        "See http://docs.corda.net/cordapp-build-systems.html")
            } else {
                project.logger.info("Including dependency in CorDapp JAR: $it")
            }
        }
        return filteredDeps.toUniqueFiles(runtimeConfiguration) - excludeDeps.toUniqueFiles(runtimeConfiguration)
    }

    private fun checkPlatformVersionInfo(): Pair<Int, Int> {
        // If the minimum platform version is not set, default to 1.
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion ?: cordapp.info.minimumPlatformVersion ?: 1
        val targetPlatformVersion = cordapp.targetPlatformVersion ?: cordapp.info.targetPlatformVersion
                ?: throw InvalidUserDataException("Target versionId was not set and could not be determined from the project's Corda dependency. Please specify the target versionId of your CorDapp.")
        if (targetPlatformVersion < 1) {
            throw InvalidUserDataException("Target versionId must not be smaller than 1.")
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw InvalidUserDataException("Target versionId must not be smaller than min platform versionId.")
        }
        return Pair(targetPlatformVersion, minimumPlatformVersion)
    }

    private fun parseVersion(versionStr: String?): Int {
        if (versionStr == null)
            throw InvalidUserDataException("Target versionId not specified. Please specify a whole number starting from 1.")
        return try {
            val version = Integer.parseInt(versionStr)
            if (version < 1) {
                throw InvalidUserDataException("Target versionId must not be smaller than 1.")
            }
            return version
        } catch (e: NumberFormatException) {
            throw InvalidUserDataException("Version identifier must be a whole number starting from 1.")
        }
    }

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return map { configuration.files(it) }.flatten().toSet()
    }
}
