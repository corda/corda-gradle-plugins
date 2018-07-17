package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.io.FileInputStream
import java.util.*
import java.util.regex.Pattern

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
@Suppress("UNUSED")
class CordappPlugin : Plugin<Project> {
    private companion object {
        private const val UNKNOWN = "Unknown"
    }

    /**
     * CorDapp's information.
     */
    lateinit var cordapp: CordappExtension

    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        verifyGradleVersion(project)

        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)
        Utils.createRuntimeConfiguration("cordaRuntime", project)

        cordapp = project.extensions.create("cordapp", CordappExtension::class.java)
        cordapp.setProject(project)

        configureCordappJar(project)
    }

    /**
     * Verifies that the version of Gradle the project is built with matches gradle-wrapper config if one exists
     */
    private fun verifyGradleVersion(project: Project) {
        val gradleWrapperConfig = File(project.rootProject.projectDir!!.absolutePath + "/gradle/wrapper/gradle-wrapper.properties")
        if (gradleWrapperConfig.exists()) {
            val props = Properties()
            props.load(FileInputStream(gradleWrapperConfig))
            val distributionUrl = props.getProperty("distributionUrl")
            if (distributionUrl != null) {
                val matcher = Pattern.compile(".*-(.*?)-[a-zA-Z]+.zip").matcher(distributionUrl)!!
                if (matcher.matches() && matcher.groupCount() == 1) {
                    val gradleWrapperVersion = matcher.group(1)!!
                    val gradleProjectVersion = project.gradle.gradleVersion!!
                    if (gradleWrapperVersion != gradleProjectVersion) {
                        throw GradleException("Invalid Gradle version. Expected $gradleWrapperVersion but was $gradleProjectVersion. Please use ./gradlew to build the project. More information can be found here: https://docs.corda.net/cordapp-build-systems.html")
                    }
                } else {
                    project.logger.warn("Couldn't determine Gradle version from the distributionUrl=$distributionUrl. Skipping Gradle version check.")
                }
            } else {
                project.logger.warn("distributionUrl was not found in Gradle wrapper configuration. Skipping Gradle version check.")
            }
        } else {
            project.logger.warn("Gradle wrapper configuration was not found in ${gradleWrapperConfig.path}. Skipping Gradle version check.")
        }
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
            attributes["Name"] = cordapp.info?.name ?: "${project.group}.${jarTask.baseName}"
            attributes["Implementation-Version"] = cordapp.info?.version ?: project.version
            attributes["Implementation-Vendor"] = cordapp.info?.vendor ?: UNKNOWN
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

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return map { configuration.files(it) }.flatten().toSet()
    }
}
