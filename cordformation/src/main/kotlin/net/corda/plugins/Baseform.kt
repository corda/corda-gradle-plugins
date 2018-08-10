package net.corda.plugins

import groovy.lang.Closure
import net.corda.cordform.CordformDefinition
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.slf4j.Logger
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
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
        const val nodeJarName = "corda.jar"
        private val defaultDirectory: Path = Paths.get("build", "nodes")
    }

    /**
     * Optionally the name of a CordformDefinition subclass to which all configuration will be delegated.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var definitionClass: String? = null

    var directory = defaultDirectory

    @Nested
    @Input
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
     * Sets the directory to install nodes into.
     * This provides a Gradle-friendly [File] interface.
     *
     * @param directory The directory the nodes will be installed into.
     */
    fun directory(directory: File) {
        this.directory = directory.toPath()
    }

    /**
     * Default configuration values that are applied to every node.
     */
    @Optional
    @Nested
    @Input
    var nodeDefaults: Closure<in Node>? = null

    /**
     * Add a node configuration.
     *
     * @param configureClosure A node configuration that will be deployed.
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureClosure: Closure<in Node>) {
        val newNode = configureDefaults(Node(project))
        (project.configure(newNode, configureClosure) as Node).also { node ->
            nodes += node
        }
    }

    /**
     * Add a node configuration
     *
     * @param configureFunc A node configuration that will be deployed
     */
    @Suppress("MemberVisibilityCanPrivate")
    fun node(configureFunc: Node.() -> Any?): Node {
        return configureDefaults(Node(project)).also { node ->
            node.configureFunc()
            nodes += node
        }
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
        return CordformClassLoader(urls, logger)
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
        return URLClassLoader(urls).loadClass("net.corda.nodeapi.internal.network.NetworkBootstrapper")
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
                project.logger.info("User has used '$directory', default directory is '$defaultDirectory'")
                "'directory' cannot be used when 'definitionClass' is specified. Use CordformDefinition.nodesDirectory instead."
            }
            directory = cd.nodesDirectory
            deleteRootDir()
            val cordapps = cd.cordappDependencies
            cd.nodeConfigurers.forEach {
                val node = node(::configureDefaults)
                it.accept(node)
                cordapps.forEach { app ->
                    if (app.mavenCoordinates != null) {
                        node.cordapp(project.project(app.mavenCoordinates!!))
                    } else {
                        node.cordapp(app.projectName!!)
                    }
                }
                node.rootDir(directory)
            }
            cd.setup { nodeName -> project.projectDir.toPath().resolve(getNodeByName(nodeName)!!.nodeDir.toPath()) }
        } else {
            deleteRootDir()
            nodes.forEach {
                it.rootDir(directory)
            }
        }
    }

    private fun configureDefaults(node: Node): Node {
        return nodeDefaults?.let { project.configure(node, it) as Node } ?: node
    }

    private fun deleteRootDir() {
        project.logger.info("Deleting $directory")
        project.delete(directory)
    }

    protected fun bootstrapNetwork() {
        val networkBootstrapperClass = loadNetworkBootstrapperClass()
        val networkBootstrapper = networkBootstrapperClass.newInstance()
        val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrap", Path::class.java, List::class.java).apply { isAccessible = true }
        val allCordapps = nodes.flatMap(Node::getCordappList).map { it.jarFile }.distinct()
        val rootDir = project.projectDir.toPath().resolve(directory).toAbsolutePath().normalize()
        try {
            // Call NetworkBootstrapper.bootstrap
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

    /**
     * This classloader ensures that classes in the Gradle project cannot access anything on
     * the Gradle plugins classpath. The only exceptions to this are the net.corda.cordform.*
     * classes and their dependencies (Kotlin and Typesafe Config), because these classes
     * exist on both the project and the plugins classpaths.
     *
     * This plugin MUST load every net.corda.cordform.* class from the same classloader to
     * ensure consistency. E.g. two [CordformDefinition] classes are assignment-incompatible
     * if they have different classloaders.
     *
     * Note that this classloader's parent is the system classloader.
     */
    class CordformClassLoader(
        urls: Array<URL>,
        private val logger: Logger,
        cordform: Class<*> = CordformDefinition::class.java // parameterised for testing
)   : URLClassLoader(urls) {
        private val pluginClassloader: ClassLoader = cordform.classLoader
        private val prefixes: List<String>
                      = listOf(cordform.`package`.name + '.', "com.typesafe.config.", "org.jetbrains.kotlin.")

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            synchronized(getClassLoadingLock(name)) {
                if (prefixes.any { name.startsWith(it) }) {
                    logger.debug("-- load from plugins --> {}", name)
                    return pluginClassloader.loadClass(name)
                }
                return super.loadClass(name, resolve)
            }
        }
    }
}
