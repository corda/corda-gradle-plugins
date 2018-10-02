package net.corda.plugins

import net.corda.plugins.Utils.Companion.compareVersions
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarInputStream

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
            throw GradleException("Gradle version ${project.gradle.gradleVersion} is below the supported minimum version $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)
        Utils.createRuntimeConfiguration("cordaRuntime", project)

        cordapp = project.extensions.create("cordapp", CordappExtension::class.java)
        cordapp.setProject(project)
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
            val (targetVersion, minPlatformVersion) = getAndCheckVersionInfo(project)
            val attributes = jarTask.manifest.attributes
            attributes["Name"] = cordapp.info?.name ?: "${project.group}.${jarTask.baseName}"
            attributes["Implementation-Version"] = cordapp.info?.version ?: project.version
            attributes["Implementation-Vendor"] = cordapp.info?.vendor ?: UNKNOWN
            targetVersion?.let { attributes["Target-Platform-Version"] = it }
            minPlatformVersion?.let { attributes["Min-Platform-Version"] = it }
            if (attributes["Implementation-Vendor"] == UNKNOWN) {
                project.logger.warn("CordApp's vendor is \"$UNKNOWN\". Please specify it in \"cordapp.info.vendor\".")
            }
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

        val signTask = project.task("signCordappJar")
        signTask.doLast {
            if (cordapp.signing.enabled) {
                val options = cordapp.signing.options.toSignJarOptionsMap()
                options["jar"] = project.tasks.getByName("jar").outputs.files.singleFile.toPath().toString()
                project.ant.invokeMethod("signjar", options)
            }
        }
        jarTask.finalizedBy(signTask)
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

    private fun getAndCheckVersionInfo(project: Project): Pair<Int?, Int?> {
        // If the target version is not set, try to determine the platformversion of the Corda jar this project
        // depends on and use that.
        val targetVersion: Int? =
                if (cordapp.info?.targetVersion == null) {
                    project.logger.warn("No target version specified. Attempting to determine platform version from the project's Corda dependency.")
                    val platformVersion = getPlatformVersion(project)
                    if (platformVersion == null) {
                        project.logger.warn("Could not determine platform version from the project's Corda dependency.")
                    } else {
                        project.logger.info("Setting target version to ${platformVersion}.")
                    }
                    platformVersion
                } else {
                    cordapp.info?.targetVersion
                }

        // If the minimum platform version is not set, default to 1.
        val minPlatformVersion: Int = cordapp.info?.minPlatformVersion ?: 1
        if (targetVersion == null) {
            throw InvalidUserDataException("Target version was not set and could not be determined from the project's Corda dependency. Please specify the target version of your CorDapp.")
        }
        if (targetVersion < 1) {
            throw InvalidUserDataException("Target version must not be smaller than 1.")
        }
        if (targetVersion < minPlatformVersion) {
            throw InvalidUserDataException("Target version must not be smaller than min platform version.")
        }
        return Pair(targetVersion, minPlatformVersion)
    }

    /**
     * Retrieve the Corda platform version from the project's Corda jar (if present)
     *
     * @param project The project environment this plugin executes in.
     * @return The platform version of the projects Corda jar, or null if it cannot be determined.
     */
    private fun getPlatformVersion(project: Project): Int? {
        val releaseVersion =
                try {
                    project.rootProject.ext<String>("corda_release_version")
                } catch (e: ExtraPropertiesExtension.UnknownPropertyException) {
                    project.logger.warn(e.message)
                    null
                } ?: return null
        val maybeJar = project.configuration("runtime").filter {
            "corda-$releaseVersion.jar" in it.toString() || "corda-enterprise-$releaseVersion.jar" in it.toString()
        }
        if (!maybeJar.isEmpty && maybeJar.singleFile.isFile) {
            return maybeJar.singleFile.toURI().toURL()
                    .openStream().let(::JarInputStream)
                    .use { it.manifest }.mainAttributes?.getValue("Corda-Platform-Version")?.toInt()
        }
        return null
    }

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return map { configuration.files(it) }.flatten().toSet()
    }
}
