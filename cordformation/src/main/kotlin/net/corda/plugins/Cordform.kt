package net.corda.plugins

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused", "UnstableApiUsage")
@DisableCachingByDefault
open class Cordform @Inject constructor(
    objects: ObjectFactory,
    fs: FileSystemOperations,
    layout: ProjectLayout
) : Baseform(objects, fs, layout) {
    init {
        description = "Creates and configures a deployment of Corda Node directories."
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
        fs.copy {
            it.apply {
                from(Cordformation.getPluginFile(this@Cordform, "runnodes.jar"))
                fileMode = Cordformation.executableFileMode
                into(directory)
            }
        }

        fs.copy {
            it.apply {
                from(Cordformation.getPluginFile(this@Cordform, "runnodes"))
                // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
                filter(mapOf("eol" to FixCrLfFilter.CrLf.newInstance("lf")), FixCrLfFilter::class.java)
                fileMode = Cordformation.executableFileMode
                into(directory)
            }
        }

        fs.copy {
            it.apply {
                from(Cordformation.getPluginFile(this@Cordform, "runnodes.bat"))
                into(directory)
            }
        }
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        logger.lifecycle("Running Cordform task")
        initializeConfiguration()
        nodes.forEach {
            if (it.p2pAddress == null) {
                throw IllegalStateException("p2pAddress / p2pPort is required when not running dockerized nodes, it is missing in ${it.name}")
            }
        }
        nodes.forEach(Node::installConfig)
        nodes.forEach(Node::installDrivers)
        installCordaJar()
        generateExcludedWhitelist()
        generateKeystoreAndSignCordappJar()
        installRunScript()
        bootstrapNetwork()
        nodes.forEach(Node::build)
    }
}
