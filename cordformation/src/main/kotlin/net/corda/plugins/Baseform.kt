package net.corda.plugins

import groovy.lang.Closure
import net.corda.cordform.CordformDefinition
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarInputStream

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Baseform : DefaultTask() {
    private companion object {
        val nodeJarName = "corda.jar"
        private val defaultDirectory: Path = Paths.get("build", "nodes")
    }

    /**
     * Optionally the name of a CordformDefinition subclass to which all configuration will be delegated.
     */
    @Suppress("MemberVisibilityCanPrivate")
    var definitionClass: String? = null
    var directory = defaultDirectory
    protected val nodes = mutableListOf<Node>()

    /**
     * Set the directory to install nodes into.
     *
     * @param directory The directory the nodes will be installed into.
     */
    fun directory(directory: String) {
        this.directory = Paths.get(directory)
    }

    /**
     * Add a node configuration.
     *
     * @param configureClosure A node configuration that will be deployed.
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureClosure: Closure<in Node>) {
        nodes += project.configure(Node(project), configureClosure) as Node
    }

    /**
     * Add a node configuration
     *
     * @param configureFunc A node configuration that will be deployed
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureFunc: Node.() -> Any?): Node {
        val node = Node(project).apply { configureFunc() }
        nodes += node
        return node
    }

    /**
     * Returns a node by name.
     *
     * @param name The name of the node as specified in the node configuration DSL.
     * @return A node instance.
     */
    private fun getNodeByName(name: String): Node? = nodes.firstOrNull { it.name == name }

    /**
     * The definitionClass needn't be compiled until just before our build method, so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private fun loadCordformDefinition(): CordformDefinition {
        val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
        val classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, CordformDefinition::class.java.classLoader)
                .loadClass(definitionClass)
                .asSubclass(CordformDefinition::class.java)
                .newInstance()
    }

    /**
     * The NetworkBootstrapper needn't be compiled until just before our build method, so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private fun loadNetworkBootstrapperClass(): Class<*> {
        val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
        val classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, javaClass.classLoader).loadClass("net.corda.nodeapi.internal.network.NetworkBootstrapper")
    }

    /**
     * Installs the corda fat JAR to the root directory, for the network bootstrapper to use.
     */
    protected fun installCordaJar() {
        val cordaJar = Cordformation.verifyAndGetRuntimeJar(project, "corda")
        project.copy {
            it.apply {
                from(cordaJar)
                into(directory)
                rename(cordaJar.name, nodeJarName)
                fileMode = Cordformation.executableFileMode
            }
        }
    }

    internal fun initializeConfiguration() {
        if (definitionClass != null) {
            val cd = loadCordformDefinition()
            // If the user has specified their own directory (even if it's the same default path) then let them know
            // it's not used and should just rely on the one in CordformDefinition
            require(directory === defaultDirectory) {
                project.logger.info("User has used '$directory', default directory is '${defaultDirectory}'")
                "'directory' cannot be used when 'definitionClass' is specified. Use CordformDefinition.nodesDirectory instead."
            }
            directory = cd.nodesDirectory
            val cordapps = cd.cordappDependencies
            cd.nodeConfigurers.forEach {
                val node = node { }
                it.accept(node)
                cordapps.forEach {
                    if (it.mavenCoordinates != null) {
                        node.cordapp(project.project(it.mavenCoordinates!!))
                    } else {
                        node.cordapp(it.projectName!!)
                    }
                }
                node.rootDir(directory)
            }
            cd.setup { nodeName -> project.projectDir.toPath().resolve(getNodeByName(nodeName)!!.nodeDir.toPath()) }
        } else {
            nodes.forEach {
                it.rootDir(directory)
            }
        }
    }
    protected fun bootstrapNetwork() {
        val networkBootstrapperClass = loadNetworkBootstrapperClass()
        val networkBootstrapper = networkBootstrapperClass.newInstance()
        val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrap", Path::class.java, List::class.java).apply { isAccessible = true }
        // Call NetworkBootstrapper.bootstrap
        try {
            // Create a list of all cordapps used in this network and pass it to the bootstrapper.
            val allCordapps = nodes.flatMap { it.getCordappList() }.map { it.jarFile }.map { it.absolutePath }
            val rootDir = project.projectDir.toPath().resolve(directory).toAbsolutePath().normalize()
            bootstrapMethod.invoke(networkBootstrapper, rootDir, allCordapps)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }

    private fun File.containsPackage(`package`: String): Boolean {
        JarInputStream(inputStream()).use {
            while (true) {
                val name = it.nextJarEntry?.name ?: break
                if (name.endsWith(".class") && name.replace('/', '.').startsWith(`package`)) {
                    return true
                }
            }
            return false
        }
    }
}
