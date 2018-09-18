package net.corda.plugins

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

    @Input
    var directory = defaultDirectory

    @Nested
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
     * This should ideally be a [Nested] property, but Gradle doesn't
     * support this for a [Closure]. However, these defaults are
     * applied to every node anyway so [Internal] should be fine.
     */
    @Optional
    @Internal
    var nodeDefaults: Closure<in Node>? = null

    /**
     * Optional parameters for ant keyGen and signJar tasks to sing Cordapps
     */
    @Input
    var signing: Signing = Signing()

    fun signing(configureClosure: Closure<in Signing>) {
        signing = project.configure(Signing(), configureClosure) as Signing
    }

    /**
     * Optional list of classes to be included in "exclude_whitelist.txt"  file for network-bootstrapper.
     */
    @Input
    var excludeWhitelist: MutableList<String> = mutableListOf()

    fun excludeWhitelist(map: List<String> ) {
        this.excludeWhitelist.addAll(map)
    }

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
        deleteRootDir()
        nodes.forEach {
            it.rootDir(directory)
        }
    }

    private fun configureDefaults(node: Node): Node {
        return nodeDefaults?.let { project.configure(node, it) as Node } ?: node
    }

    private fun deleteRootDir() {
        project.logger.info("Deleting $directory")
        project.delete(directory)
    }

    protected fun generateExcludedWhitelist() {
        if (excludeWhitelist.isNotEmpty()) {
            val rootDir = Paths.get(project.projectDir.toPath().resolve(directory).resolve("exclude_whitelist.txt").toAbsolutePath().normalize().toString())
            Files.write(rootDir, excludeWhitelist)
         }
    }

    /** invokes ANT tasks GenKey to generate keyStore (optionally) and SignJar task to sign the generated Cordapp/optionally other Cordapps deployed to nodes */
    protected fun generateKeystoreAndSignCordappJar() {
        if (!signing.enabled)
            return

        val baseKeystoreDir = project.projectDir.toPath().resolve(directory)

        if (signing.generateKeystore) {
            val genKeyTaskOptions = signing.genKeyTaskOptions(baseKeystoreDir)
            Files.deleteIfExists(Paths.get(genKeyTaskOptions["keystore"]))

            if (signing.hasDefaultKeystoreOptions(baseKeystoreDir)) {
                project.logger.warn("Generating default keystore to sign Cordapps '${genKeyTaskOptions["keystore"]}'. " +
                        "To use custom keystore provide keystore details, see documentation at https://docs.corda.net/generating-a-node.html.")
            } else {
                project.logger.warn("Generating keystore to sign Cordapps '${genKeyTaskOptions["keystore"]}'")
            }
            project.ant.invokeMethod("genkey", genKeyTaskOptions)
        }

        val signJarTaskOptions = signing.singJarTaskOptions(baseKeystoreDir, project.tasks.getByName("jar").outputs.files.singleFile.toPath())
        project.ant.invokeMethod("signjar", signJarTaskOptions)

        if (signing.all) {
            val allCordapps = nodes.flatMap(Node::getCordappList).map { it.jarFile }.distinct()
            allCordapps.forEach {
                val signJarTaskOptions = signing.singJarTaskOptions(baseKeystoreDir, it)
                project.ant.invokeMethod("signjar", signJarTaskOptions)
            }
        }
    }

    protected fun bootstrapNetwork() {
        val networkBootstrapperClass = loadNetworkBootstrapperClass()
        val networkBootstrapper = networkBootstrapperClass.newInstance()
        val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrapCordform", Path::class.java, List::class.java).apply { isAccessible = true }
        val allCordapps = nodes.flatMap(Node::getCordappList).map { it.jarFile }.distinct()
        val rootDir = project.projectDir.toPath().resolve(directory).toAbsolutePath().normalize()
        try {
            // Call NetworkBootstrapper.bootstrap
            bootstrapMethod.invoke(networkBootstrapper, rootDir, allCordapps)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }
}
