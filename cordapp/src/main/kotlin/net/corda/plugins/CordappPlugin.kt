package net.corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
class CordappPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)

        val configuration: Configuration = project.configurations.create("cordaRuntime")
        configuration.isTransitive = false
        project.configurations.single { it.name == "runtime" }.extendsFrom(configuration)

        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private fun configureCordappJar(project: Project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        val task = project.task("configureCordappFatJar")
        val jarTask = project.tasks.getByName("jar") as Jar
        task.doLast {
            jarTask.from(getDirectNonCordaDependencies(project).map { project.zipTree(it)}).apply {
                exclude("META-INF/*.SF")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.RSA")
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
        project.logger.debug("Full list of Corda dependencies to exclude from CorDapp JAR")
        excludeDeps.sortedBy { it.name }.forEach { project.logger.debug("${it.group} : ${it.name} : ${it.version}") }
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        project.logger.debug("Direct dependencies from the Project, before they are filtered")
        directDeps.sortedBy { it.name }.forEach  { project.logger.debug("${it.group} : ${it.name} : ${it.version}") }
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar,
        // but we must consider that the dependency name also includes its version number, which we don't want
        // to include when attempting to match the name of the dependency
        val filteredDeps = directDeps.filter { dep ->
            excludes.none { exclude -> (exclude["group"] == dep.group) && (exclude["name"] == dep.name) }
        }
        project.logger.debug("Filtered list of direct dependencies")
        filteredDeps.sortedBy { it.name }.forEach  { project.logger.debug("${it.group} : ${it.name} : ${it.version}") }
        filteredDeps.forEach {
            // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
            val group = it.group ?: ""
            if (group.startsWith("net.corda.") || group.startsWith("com.r3.corda.enterprise.")) {
                project.logger.warn("You appear to have included a Corda platform component ($it) using a 'compile' or 'runtime' dependency." +
                        "This can cause node stability problems. Please use 'corda' instead." +
                        "See http://docs.corda.net/cordapp-build-systems.html")
            } else {
                project.logger.info("Including dependency in CorDapp JAR: $it")
            }
        }
        val dependencies = filteredDeps.map { runtimeConfiguration.files(it) }.flatten().sortedBy { it.name }.toSet()
        project.logger.info("Final set of direct non-corda dependencies to include in CorDapp JAR")
        dependencies.forEach { project.logger.info("${it.name} : ${it.absolutePath}") }
        return dependencies
    }
}
