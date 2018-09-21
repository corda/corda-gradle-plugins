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
     * Options for sign Cordapp JARs and generate keyStore for it,
     * by default all Cordapps are signed with default keyStore options.
     */
    @Input
    var signing: Signing = Signing(project)

    fun signing(configureClosure: Closure<in Signing>) {
        signing = project.configure(Signing(project), configureClosure) as Signing
    }

    /**
     * List of classes to be included in "exclude_whitelist.txt" file for network-bootstrapper,
     * by default contains contracts classes from Finance Cordapp to allow signature constraints being used for them.
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
     * If excludeWhitelist is empty and all Cordapp JARs will be signed, adds Finance App contract classes to exclude_whitelist.txt.
     */
    protected fun generateExcludedWhitelist() {
        if (signing.enabled && excludeWhitelist.isEmpty()) {
            if (signing.all) {
                // If user didn't specified exclude whitelist and signs all Cordapps (so potentially corda-finance/Finance app)
                // then allow contracts from Finance app to work with signature constraints and not whitelisting by default.
                excludeWhitelist = listOf("net.corda.finance.contracts.asset.Cash", "net.corda.finance.contracts.asset.CommercialPaper",
                        "net.corda.finance.contracts.CommercialPaper", "net.corda.finance.contracts.JavaCommercialPaper")
                logger.warn("Signing Cordapp JARs but no contract classes are excluded from whitelisting " +
                        "and signature constraints will not be used for contract classes except ones from corda-finance JAR.")
            } else {
                logger.warn("Signing Cordapp JAR but no contract classes are excluded from whitelisting " +
                        "and signature constraints will not be used for contract classes.")
            }
        }
        if (excludeWhitelist.isNotEmpty()) {
            val rootDir = Paths.get(project.projectDir.toPath().resolve(directory).resolve("exclude_whitelist.txt").toAbsolutePath().normalize().toString())
            Files.write(rootDir, excludeWhitelist)
        }
    }

    /**
     * Optionally generate keyStore and sign the generated Cordapp/other Cordapps deployed to nodes.
     */
    protected fun generateKeystoreAndSignCordappJar() {
        if (!signing.enabled)
            return

        val addDefaultKeystoreIfAbsent = { map: MutableMap<String, String> ->
            map.putIfAbsent("keystore",
                    project.projectDir.toPath().resolve(directory).resolve("jarSignKeystore.p12").toAbsolutePath().normalize().toString())
        }

        if (signing.generateKeystore) {
            val genKeyTaskOptions = signing.options.toGenKeyOptionsMap()
            addDefaultKeystoreIfAbsent(genKeyTaskOptions)
            if (Files.exists(Paths.get(genKeyTaskOptions["keystore"]))) {
                logger.warn("Skipping keystore generation to sign Cordapps, the keystore already exists at '${genKeyTaskOptions["keystore"]}'.")
            } else {
                logger.info("Generating keystore to sign Cordapps with options: ${genKeyTaskOptions.entries.map { "${it.key}=\"${it.value}\"" }.joinToString()}.")
                project.ant.invokeMethod("genkey", genKeyTaskOptions)
            }
        }

        val signJarOptions = signing.options.toSignJarOptionsMap()
        addDefaultKeystoreIfAbsent(signJarOptions)
        val jarsToSign = mutableListOf(project.tasks.getByName("jar").outputs.files.singleFile.toPath()) +
                if (signing.all) nodes.flatMap(Node::getCordappList).map { it.jarFile }.distinct() else emptyList()
        jarsToSign.forEach {
            signJarOptions["jar"] = it.toString()
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
