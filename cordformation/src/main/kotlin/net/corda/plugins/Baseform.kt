package net.corda.plugins

import groovy.lang.Closure
import net.corda.plugins.SigningOptions.Companion.DEFAULT_KEYSTORE_EXTENSION
import net.corda.plugins.SigningOptions.Companion.DEFAULT_KEYSTORE_FILE
import net.corda.plugins.Utils.Companion.createTempFileFromResource
import org.gradle.api.Action
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
     * Configuration for keystore generation and JAR signing.
     */
    @Input
    var signing: KeyGenAndSigning = project.objects.newInstance(KeyGenAndSigning::class.java)

    fun signing(action: Action<in KeyGenAndSigning>) {
        action.execute(signing)
    }

    /**
     * List of classes to be included in "exclude_whitelist.txt" file for network-bootstrapper.
     */
    @Input
    var excludeWhitelist: List<String> = listOf()

    fun excludeWhitelist(map: List<String>) {
        excludeWhitelist = map
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

    /**
     * Generates exclude_whitelist.txt.
     */
    protected fun generateExcludedWhitelist() {
        if (excludeWhitelist.isNotEmpty()) {
            var fileName = "exclude_whitelist.txt"
            logger.debug("Adding $excludeWhitelist to $fileName.")
            val rootDir = Paths.get(project.projectDir.toPath().resolve(directory).resolve(fileName).toAbsolutePath().normalize().toString())
            Files.write(rootDir, excludeWhitelist)
        }
    }

    /**
     * Optionally generate keyStore and sign the generated Cordapp/other Cordapps deployed to nodes.
     */
    protected fun generateKeystoreAndSignCordappJar() {
        if (!signing.enabled)
            return

        require(!signing.generateKeystore || (signing.generateKeystore && !signing.options.hasDefaultOptions())) {
            "Mis-configured keyStore generation to sign CorDapp JARs. When 'signing.generateKeystore' is true the following " +
                    "'signing.options' need to be configured: keystore, alias, storepass, keypass."
        }

        if (signing.generateKeystore && !signing.options.hasDefaultOptions()) {
            val genKeyTaskOptions = signing.options.toGenKeyOptionsMap()
            if (Files.exists(Paths.get(genKeyTaskOptions[SigningOptions.Key.KEYSTORE]))) {
                logger.warn("Skipping keystore generation to sign Cordapps, the keystore already exists at '${genKeyTaskOptions[SigningOptions.Key.KEYSTORE]}'.")
            } else {
                logger.info("Generating keystore to sign Cordapps with options: ${genKeyTaskOptions.map { "${it.key}=${it.value}" }.joinToString()}.")
                project.ant.invokeMethod("genkey", genKeyTaskOptions)
            }
        }

        val signJarOptions = signing.options.toSignJarOptionsMap()
        if (signing.options.hasDefaultOptions()) {
            val keyStorePath = createTempFileFromResource(SigningOptions.DEFAULT_KEYSTORE, DEFAULT_KEYSTORE_FILE, DEFAULT_KEYSTORE_EXTENSION)
            signJarOptions[SigningOptions.Key.KEYSTORE] = keyStorePath.toString()
        }

        val jarsToSign = mutableListOf(project.tasks.getByName(SigningOptions.Key.JAR).outputs.files.singleFile.toPath()) +
                if (signing.all) nodes.flatMap(Node::getCordappList).map { it.jarFile }.distinct() else emptyList()
        jarsToSign.forEach {
            signJarOptions[SigningOptions.Key.JAR] = it.toString()
            project.ant.invokeMethod("signjar", signJarOptions)
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
