package net.corda.plugins

import com.typesafe.config.ConfigRenderOptions
import groovy.lang.Closure
import net.corda.plugins.cordformation.signing.SigningOptions
import net.corda.plugins.cordformation.signing.SigningOptions.Companion.DEFAULT_KEYSTORE_EXTENSION
import net.corda.plugins.cordformation.signing.SigningOptions.Companion.DEFAULT_KEYSTORE_FILE
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.model.ObjectFactory
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
import java.security.Security

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Baseform(objects: ObjectFactory) : DefaultTask() {

    private companion object {
        const val nodeJarName = "corda.jar"
        const val GROUP_NAME = "Cordformation"
    }

    init {
        group = GROUP_NAME
    }

    @get:Input
    var directory: Path = project.buildDir.toPath().resolve("nodes")

    @get:Nested
    protected val nodes = mutableListOf<Node>()

    @get:Optional
    @get:Nested
    protected val networkParameterOverrides: NetworkParameterOverrides = objects.newInstance(NetworkParameterOverrides::class.java, project)

    fun networkParameterOverrides(action: Action<in NetworkParameterOverrides>) {
        action.execute(networkParameterOverrides)
    }

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
    @get:Internal
    var nodeDefaults: Closure<in Node>? = null
        protected set

    /**
     * Configuration for keystore generation and JAR signing.
     */
    @get:Input
    val signing: KeyGenAndSigning = objects.newInstance(KeyGenAndSigning::class.java)

    fun signing(action: Action<in KeyGenAndSigning>) {
        action.execute(signing)
    }

    /**
     * List of classes to be included in "exclude_whitelist.txt" file for network-bootstrapper.
     */
    @get:Input
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
     * The NetworkBootstrapper needn't be compiled until just before our build method,
     * so we load it manually via sourceSets.main.runtimeClasspath.
     */
    private fun createNetworkBootstrapperLoader(): URLClassLoader {
        val plugin = project.convention.getPlugin(JavaPluginConvention::class.java)
        val classpath = plugin.sourceSets.getByName(MAIN_SOURCE_SET_NAME).runtimeClasspath
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        // This classloader should be self-contained. Don't assign Gradle's classloader as its parent.
        return URLClassLoader(urls, null)
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
        logger.lifecycle("Deleting $directory")
        project.delete(directory)
    }

    /**
     * Generates exclude_whitelist.txt.
     */
    protected fun generateExcludedWhitelist() {
        if (excludeWhitelist.isNotEmpty()) {
            val fileName = "exclude_whitelist.txt"
            logger.debug("Adding {} to {}.", excludeWhitelist, fileName)
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
                logger.lifecycle("Generating keystore to sign Cordapps with options: ${genKeyTaskOptions.map { "${it.key}=${it.value}" }.joinToString()}.")
                project.ant.invokeMethod("genkey", genKeyTaskOptions)
            }
        }

        val signJarOptions = signing.options.toSignJarOptionsMap()
        if (signing.options.hasDefaultOptions()) {
            val keyStorePath = createTempFileFromResource(SigningOptions.DEFAULT_KEYSTORE, DEFAULT_KEYSTORE_FILE, DEFAULT_KEYSTORE_EXTENSION)
            signJarOptions[SigningOptions.Key.KEYSTORE] = keyStorePath.toString()
        }

        val jarsToSign = mutableListOf(project.tasks.getByName(SigningOptions.Key.JAR).outputs.files.singleFile.toPath()) +
                if (signing.all) nodes.flatMap(Node::getCordappList).map(Node.ResolvedCordapp::jarFile).distinct() else emptyList()
        jarsToSign.forEach {
            signJarOptions[SigningOptions.Key.JAR] = it.toString()
            try{
                project.ant.invokeMethod("signjar", signJarOptions)
            }catch (e: Exception){
                throw InvalidUserDataException("Exception while signing ${it.fileName}, " +
                        "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                        "or disable signing by 'cordapp.signing.enabled false'. " +
                        if (logger.isInfoEnabled || logger.isDebugEnabled) "Search for 'ant:signjar' in log output."
                        else "Run with --info or --debug option and search for 'ant:signjar' in log output. ", e)
            }
        }
    }

    protected fun bootstrapNetwork() {
        createNetworkBootstrapperLoader().use { cl ->
            val networkBootstrapperClass = cl.loadNetworkBootstrapper()
            val allCordapps = nodes.flatMap(Node::getCordappList).map(Node.ResolvedCordapp::jarFile).distinct()
            val rootDir = project.projectDir.toPath().resolve(directory).toAbsolutePath().normalize()
            try {
                // Call NetworkBootstrapper.bootstrap
                invokeBootstrap(networkBootstrapperClass, rootDir, allCordapps)
            } catch (e: InvocationTargetException) {
                throw e.cause!!.let { InvalidUserDataException(it.message ?: "", it) }
            } finally {
                // Clean up anything else that could prevent the
                // Network Bootstrapper jar from being unloaded.
                cleanupNetworkBootstrapper(cl)
            }
        }
    }

    private fun cleanupNetworkBootstrapper(classLoader: ClassLoader) {
        /*
         * Ensure we remove any [SecurityProvider] instances that the
         * Network Bootstrapper may have registered. Otherwise the JVM
         * will be unable to delete this [ClassLoader] from memory.
         */
        Security.getProviders().forEach { provider ->
            if (provider::class.java.classLoader == classLoader) {
                Security.removeProvider(provider.name)
            }
        }

        /*
         * Shutdown the most likely SFL4J implementations that
         * Network Bootstrapper could have been using.
         */
        classLoader.shutdownLog4J2()
        classLoader.shutdownLog4J()
        classLoader.shutdownLogback()

        /*
         * Make sure JCL isn't holding onto anything either.
         */
        classLoader.shutdownCommonsLogging()
    }

    private fun invokeBootstrap(networkBootstrapperClass: Class<*>, rootDir: Path, allCordapps: List<Path>) {
        try {
            if (networkParameterOverrides.isEmpty()) {
                val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrapCordform", Path::class.java, List::class.java).apply { isAccessible = true }
                bootstrapMethod.invoke(networkBootstrapperClass.getDeclaredConstructor().newInstance(), rootDir, allCordapps)
            } else {
                val bootstrapMethod = networkBootstrapperClass.getMethod("bootstrapCordform", Path::class.java, List::class.java, String::class.java).apply { isAccessible = true }
                bootstrapMethod.invoke(networkBootstrapperClass.getDeclaredConstructor().newInstance(), rootDir, allCordapps, networkParameterOverrides.toConfig().root().render(ConfigRenderOptions.concise()))
            }
        } catch (e: NoSuchMethodException) {
            throw InvalidUserDataException("Unrecognised configuration options passed. Please ensure you're using the correct 'corda-node-api' version on Gradle's runtime classpath.", e)
        }
    }

    private fun ClassLoader.loadNetworkBootstrapper(): Class<*> {
        return try {
            loadClass("net.corda.nodeapi.internal.network.NetworkBootstrapper")
        } catch (e: ClassNotFoundException) {
            throw InvalidUserCodeException("Cannot find the NetworkBootstrapper class. Please ensure that 'corda-node-api' is available on Gradle's runtime classpath, "
                    + "e.g. by adding it to Gradle's 'runtimeOnly' configuration.", e)
        }
    }

    private fun ClassLoader.shutdownCommonsLogging() = execute("org.apache.commons.logging.LogFactory", "releaseAll") { c, m -> invoke(c, m, null) }
    private fun ClassLoader.shutdownLog4J() = execute("org.apache.log4j.LogManager", "shutdown") { c, m -> invoke(c, m, null) }
    private fun ClassLoader.shutdownLog4J2() = execute("org.apache.logging.log4j.LogManager", "shutdown") { c, m -> invoke(c, m, null) }
    private fun ClassLoader.shutdownLogback() = execute("ch.qos.logback.classic.LoggerContext", "stop") { c, m ->
        val iLogger = invoke("org.slf4j.LoggerFactory", "getILoggerFactory", null)
        invoke(c, m, iLogger)
    }

    private fun ClassLoader.execute(className: String, methodName: String, body: (String, String) -> Unit) {
        try {
            body(className, methodName)
            logger.info("Executed {}.{}() successfully", className, methodName)
        } catch (e: Exception) {
            logger.debug("Failed to execute {}.{}(): {} ({})", className, methodName, e::class.java.name, e.message)
        }
    }

    private fun ClassLoader.invoke(className: String, methodName: String, obj: Any?): Any? {
        return loadClass(className).getDeclaredMethod(methodName).invoke(obj)
    }
}
