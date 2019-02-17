package net.corda.plugins

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.tasks.TaskAction

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Cordform : Baseform() {
    internal companion object {
        const val nodeJarName = "corda.jar"
    }

    /**
     * Returns a node by name.
     *
     * @param name The name of the node as specified in the node configuration DSL.
     * @return A node instance.
     */
    private fun getNodeByName(name: String): Node? = nodes.firstOrNull { it.name == name }

    /**
     * Installs the run script into the nodes directory.
     */
    private fun installRunScript() {
        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "runnodes.jar"))
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "runnodes"))
                // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
                filter(mapOf("eol" to FixCrLfFilter.CrLf.newInstance("lf")), FixCrLfFilter::class.java)
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "runnodes.bat"))
                into("$directory/")
            }
        }
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running Cordform task")
        initializeConfiguration()
        nodes.forEach(Node::installConfig)
        installCordaJar()
        generateExcludedWhitelist()
        generateKeystoreAndSignCordappJar()
        installRunScript()
        nodes.forEach(Node::installDrivers)
        bootstrapNetwork()
        nodes.forEach(Node::build)
    }
}
