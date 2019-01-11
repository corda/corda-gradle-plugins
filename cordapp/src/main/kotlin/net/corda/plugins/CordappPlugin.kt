package net.corda.plugins

import net.corda.plugins.SignJar.Companion.sign
import net.corda.plugins.Utils.Companion.compareVersions
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
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
        configurePomCreation(project)
        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private fun configureCordappJar(project: Project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        val task = project.task("configureCordappFatJar")
        val jarTask = project.tasks.getByName("jar") as Jar
        jarTask.doFirst { _ ->
            val attributes = jarTask.manifest.attributes
            var skip = false
            // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
            if (cordapp.contract.isEmpty() && cordapp.workflow.isEmpty() && cordapp.info.isEmpty()) {
                project.logger.warn("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
            }
            else {
                // Corda 4 attributes support
                if (!cordapp.contract.isEmpty()) {
                    attributes["Cordapp-Contract-Name"] = cordapp.contract.name ?: "${project.group}.${jarTask.baseName}"
                    attributes["Cordapp-Contract-Version"] = checkCorDappVersionId(cordapp.contract.versionId)
                    attributes["Cordapp-Contract-Vendor"] = cordapp.contract.vendor ?: UNKNOWN
                    attributes["Cordapp-Contract-Licence"] = cordapp.contract.licence ?: UNKNOWN
                    skip = true
                }
                if (!cordapp.workflow.isEmpty()) {
                    attributes["Cordapp-Workflow-Name"] = cordapp.workflow.name ?: "${project.group}.${jarTask.baseName}"
                    attributes["Cordapp-Workflow-Version"] = checkCorDappVersionId(cordapp.workflow.versionId)
                    attributes["Cordapp-Workflow-Vendor"] = cordapp.workflow.vendor ?: UNKNOWN
                    attributes["Cordapp-Workflow-Licence"] = cordapp.workflow.licence ?: UNKNOWN
                    skip = true
                }
                // Deprecated support (Corda 3)
                if (!cordapp.info.isEmpty()) {
                    if (skip) {
                        project.logger.warn("Ignoring deprecated 'info' attributes. Using 'contract' and 'workflow' attributes.")
                    } else {
                        attributes["Name"] = cordapp.info.name ?: "${project.group}.${jarTask.baseName}"
                        attributes["Implementation-Version"] = cordapp.info.version ?: project.version
                        attributes["Implementation-Vendor"] = cordapp.info.vendor ?: UNKNOWN
                    }
                }
                val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
                attributes["Target-Platform-Version"] = targetPlatformVersion
                attributes["Min-Platform-Version"] = minimumPlatformVersion
                if (cordapp.sealing.enabled) {
                    attributes["Sealed"] = "true"
                }
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

    private fun isCordappProject(project: Project): Boolean{
        return try{
            project.plugins.getPlugin(net.corda.plugins.CordappPlugin::class.java)
            true
        }catch (e: UnknownPluginException){
            false
        }
    }

    private fun configurePomCreation(project: Project) {
        project.gradle.taskGraph.beforeTask { task ->
            if (task.project == project && task.name.contains("generatePomFile") && isCordappProject(project) ) {
                task.doFirst{aboutToExecute ->
                    project.logger.info("Modifying task: ${task.name} in project ${project.path} to exclude all dependencies from pom")
                    val pom = (aboutToExecute as GenerateMavenPom).pom
                    if (pom is MavenPomInternal) {
                        val filteredPom = StrippedMavenPom(pom, project) { project ->
                            hardCodedExcludes() + calculateExcludedDependencies(project).map { it.group!! to it.name }
                        }
                        aboutToExecute.pom = filteredPom
                    }
                }
            }
        }
    }

    private fun calculateExcludedDependencies(project: Project): Set<Dependency> {
        //TODO if we intend cordapp jars to define transitive dependencies instead of just being fat
        //we need to use the final artifact name, not the project name
        //for example, project(":core") needs to be translated into net.corda:corda-core

        return project.configuration("cordapp").allDependencies +
                project.configuration("cordaCompile").allDependencies +
                project.configuration("cordaRuntime").allDependencies
    }

    private fun hardCodedExcludes(): Set<Pair<String, String>>{
        val excludes = setOf(
            ("org.jetbrains.kotlin"  to "kotlin-stdlib"),
            ("org.jetbrains.kotlin" to "kotlin-stdlib-jre8"),
            ("org.jetbrains.kotlin" to "kotlin-stdlib-jdk8"),
            ("org.jetbrains.kotlin" to "kotlin-reflect"),
            ("co.paralleluniverse" to "quasar-core")
        )
        return excludes
    }

    private fun getDirectNonCordaDependencies(project: Project): Set<File> {
        project.logger.info("Finding direct non-corda dependencies for inclusion in CorDapp JAR")
        val excludes = hardCodedExcludes()

        val runtimeConfiguration = project.configuration("runtime")
        // The direct dependencies of this project
        val excludeDeps = calculateExcludedDependencies(project)
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        val filteredDeps = directDeps.filter { dep ->
            excludes.none { exclude -> (exclude.first == dep.group) && (exclude.second == dep.name) }
        }
        filteredDeps.forEach {
            // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
            val group = it.group ?: ""
            if (group.startsWith("net.corda.") || group.startsWith("com.r3.corda.")) {
                project.logger.warn(
                    "You appear to have included a Corda platform component ($it) using a 'compile' or 'runtime' dependency." +
                            "This can cause node stability problems. Please use 'corda' instead." +
                            "See http://docs.corda.net/cordapp-build-systems.html"
                )
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
        ?: throw InvalidUserDataException("CorDapp `targetPlatformVersion` was not specified in the `cordapp` metadata section.")
        if (targetPlatformVersion < 1) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than 1.")
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than the `minimumPlatformVersion` ($minimumPlatformVersion)")
        }
        return Pair(targetPlatformVersion, minimumPlatformVersion)
    }

    private fun checkCorDappVersionId(versionId: Int?): Int {
        if (versionId == null)
            throw InvalidUserDataException("CorDapp `versionId` was not specified in the associated `contract` or `workflow` metadata section. Please specify a whole number starting from 1.")
        else if (versionId < 1) {
            throw InvalidUserDataException("CorDapp `versionId` must not be smaller than 1.")
        }
        return versionId
    }

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return map { configuration.files(it) }.flatten().toSet()
    }
}
