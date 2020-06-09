package net.corda.plugins

import net.corda.plugins.CordappUtils.Companion.compareVersions
import net.corda.plugins.SignJar.Companion.sign
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.java.archives.Attributes
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.Collections.unmodifiableSet
import javax.inject.Inject

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
@Suppress("UNUSED", "UnstableApiUsage", "Deprecation")
class CordappPlugin @Inject constructor(private val objects: ObjectFactory): Plugin<Project> {
    private companion object {
        private const val UNKNOWN = "Unknown"
        private const val MIN_GRADLE_VERSION = "4.0"

        private val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
            "org.jetbrains.kotlin" to "kotlin-stdlib",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jre8",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jdk8",
            "org.jetbrains.kotlin" to "kotlin-reflect",
            "co.paralleluniverse" to "quasar-core"
        ))
    }

    /**
     * CorDapp's information.
     */
    internal lateinit var cordapp: CordappExtension
        private set

    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        if (compareVersions(project.gradle.gradleVersion, MIN_GRADLE_VERSION) < 0) {
            throw GradleException("Gradle versionId ${project.gradle.gradleVersion} is below the supported minimum versionId $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "compile", "compileOnly" and "runtime" configurations.
        project.pluginManager.apply(JavaPlugin::class.java)
        CordappUtils.createCompileConfiguration("cordapp", project.configurations)
        CordappUtils.createCompileConfiguration("cordaCompile", project.configurations)
        CordappUtils.createRuntimeConfiguration("cordaRuntime", project.configurations)

        cordapp = project.extensions.create("cordapp", CordappExtension::class.java, objects)
        configurePomCreation(project)
        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its SHA256 hash is stable!
     */
    private fun configureCordappJar(project: Project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        val jarTask = project.tasks.getByName("jar") as Jar
        jarTask.fileMode = Integer.parseInt("444", 8)
        jarTask.dirMode = Integer.parseInt("555", 8)
        jarTask.manifestContentCharset = "UTF-8"
        jarTask.isPreserveFileTimestamps = false
        jarTask.isReproducibleFileOrder = true
        jarTask.entryCompression = DEFLATED
        jarTask.includeEmptyDirs = false
        jarTask.isCaseSensitive = true
        jarTask.isZip64 = false
        jarTask.doFirst {
            val attributes = jarTask.manifest.attributes
            // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
            if (cordapp.contract.isEmpty() && cordapp.workflow.isEmpty() && cordapp.info.isEmpty()) {
                project.logger.warn("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
            } else {
                configureCordappAttributes(project, jarTask, attributes)
            }
        }.doLast {
            sign(project, cordapp.signing, it.outputs.files.singleFile, cordapp.signing.enabled.get())
        }

        val cordappTask = project.task("configureCordappFatJar")
        cordappTask.doLast {
            jarTask.from(getDirectNonCordaDependencies(project).map { file ->
                it.logger.info("CorDapp dependency: ${file.name}")
                project.zipTree(file)
            }).apply {
                exclude("META-INF/*.SF")
                exclude("META-INF/*.EC")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.RSA")
                exclude("META-INF/*.MF")
                exclude("META-INF/LICENSE*")
                exclude("META-INF/NOTICE*")
                exclude("META-INF/INDEX.LIST")
            }
        }
        jarTask.dependsOn(cordappTask)
    }

    private fun configureCordappAttributes(project: Project, jarTask: Jar, attributes: Attributes) {
        val defaultName = "${project.group}.${jarTask.archiveBaseName.get()}"
        var skip = false
        // Corda 4 attributes support
        if (!cordapp.contract.isEmpty()) {
            attributes["Cordapp-Contract-Name"] = cordapp.contract.name.getOrElse(defaultName)
            attributes["Cordapp-Contract-Version"] = checkCorDappVersionId(cordapp.contract.versionId)
            attributes["Cordapp-Contract-Vendor"] = cordapp.contract.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Contract-Licence"] = cordapp.contract.licence.getOrElse(UNKNOWN)
            skip = true
        }
        if (!cordapp.workflow.isEmpty()) {
            attributes["Cordapp-Workflow-Name"] = cordapp.workflow.name.getOrElse(defaultName)
            attributes["Cordapp-Workflow-Version"] = checkCorDappVersionId(cordapp.workflow.versionId)
            attributes["Cordapp-Workflow-Vendor"] = cordapp.workflow.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Workflow-Licence"] = cordapp.workflow.licence.getOrElse(UNKNOWN)
            skip = true
        }
        // Deprecated support (Corda 3)
        if (!cordapp.info.isEmpty()) {
            if (skip) {
                project.logger.warn("Ignoring deprecated 'info' attributes. Using 'contract' and 'workflow' attributes.")
            } else {
                attributes["Name"] = cordapp.info.name.getOrElse(defaultName)
                attributes["Implementation-Version"] = cordapp.info.version.getOrElse(project.version.toString())
                attributes["Implementation-Vendor"] = cordapp.info.vendor.getOrElse(UNKNOWN)
            }
        }
        val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
        attributes["Target-Platform-Version"] = targetPlatformVersion
        attributes["Min-Platform-Version"] = minimumPlatformVersion
        if (cordapp.sealing.enabled.get()) {
            attributes["Sealed"] = "true"
        }
    }

    private fun configurePomCreation(project: Project) {
        project.tasks.withType(GenerateMavenPom::class.java) { task ->
            task.doFirst {
                project.logger.info("Modifying task: ${task.name} in project ${project.path} to exclude all dependencies from pom")
                // The CorDapp is a semi-fat jar, so we need to exclude its compile and runtime
                // scoped dependencies from its Maven POM when we publish it.
                task.pom = filterDependenciesFor(task.pom)
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

    private fun getDirectNonCordaDependencies(project: Project): Set<File> {
        project.logger.info("Finding direct non-corda dependencies for inclusion in CorDapp JAR")

        val runtimeConfiguration = project.configuration("runtime")
        // The direct dependencies of this project
        val excludeDeps = calculateExcludedDependencies(project)
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        val filteredDeps = directDeps.filter { dep ->
            HARDCODED_EXCLUDES.none { exclude -> (exclude.first == dep.group) && (exclude.second == dep.name) }
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
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion.getOrElse(1)
        val targetPlatformVersion = cordapp.targetPlatformVersion.orNull
            ?: throw InvalidUserDataException("CorDapp `targetPlatformVersion` was not specified in the `cordapp` metadata section.")
        if (targetPlatformVersion < 1) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than 1.")
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

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return flatMapTo(LinkedHashSet()) { configuration.files(it) }
    }
}
