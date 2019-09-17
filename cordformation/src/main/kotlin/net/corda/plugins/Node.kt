package net.corda.plugins

import com.typesafe.config.*
import groovy.lang.Closure
import net.corda.plugins.utils.copyKeysTo
import net.corda.plugins.utils.copyTo
import org.apache.commons.io.FilenameUtils.removeExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * Represents a node that will be installed.
 */
open class Node @Inject constructor(private val project: Project) {
    internal data class ResolvedCordapp(val jarFile: Path, val config: String?)

    private companion object {
        const val webJarName = "corda-testserver.jar"
        const val configFileProperty = "configFile"
        const val DEFAULT_HOST = "localhost"
    }

    /**
     * Set the list of CorDapps to install to the plugins directory. Each cordapp is a fully qualified Maven
     * dependency name, eg: com.example:product-name:0.1
     *
     * @note Your app will be installed by default and does not need to be included here.
     * @note Type is any due to gradle's use of "GStrings" - each value will have "toString" called on it
     */
    var cordapps: MutableList<out Any>
        @Nested get() = internalCordapps
        @Deprecated("Use cordapp instead - setter will be removed by Corda V4.0")
        set(value) {
            value.forEach {
                cordapp(it.toString())
            }
        }

    private val internalCordapps = mutableListOf<Cordapp>()
    @get:Optional
    @get:Nested
    val projectCordapp = project.objects.newInstance(Cordapp::class.java, "", project)
    internal lateinit var nodeDir: File
        @Internal get
        private set
    private lateinit var rootDir: File
    internal lateinit var containerName: String
        @Internal get
        private set
    private var rpcSettings: RpcSettings = RpcSettings()
    private var webserverJar: String? = null
    private var p2pPort = 10002
    internal var rpcPort = 10003
        @Input get
        private set
    internal var config = ConfigFactory.empty()

    /**
     * Name of the node. Node will be placed in directory based on this name - all lowercase with whitespaces removed.
     * Actual node name inside node.conf will be as set here.
     */
    var name: String? = null
        @Input get
        private set

    /**
     * Set the RPC users for this node. This configuration block allows arbitrary configuration.
     * The recommended current structure is:
     * [[['username': "username_here", 'password': "password_here", 'permissions': ["permissions_here"]]]
     * The above is a list to a map of keys to values using Groovy map and list shorthands.
     *
     * Incorrect configurations will not cause a DSL error.
     */
    @get:Optional
    @get:Input
    var rpcUsers: List<Map<String, Any>> = emptyList()

    /**
     * Apply the notary configuration if this node is a notary. The map is the config structure of
     * net.corda.node.services.config.NotaryConfig
     */
    @get:Optional
    @get:Input
    var notary: Map<String, Any> = emptyMap()

    @get:Optional
    @get:Input
    var extraConfig: Map<String, Any> = emptyMap()

    /**
     * Copy files into the node relative directory './drivers'.
     */
    @get:Optional
    @get:Input
    var drivers: List<String>? = null

    /**
     * Get the artemis address for this node.
     *
     * @return This node's P2P address.
     */

    val p2pAddress: String?
        @Optional
        @Input
        get() = getOptionalString("p2pAddress")

    /**
     * Returns the RPC address for this node, or null if one hasn't been specified.
     */
    val rpcAddress: String?
        @Optional
        @Input
        get() {
            return if (config.hasPath("rpcSettings.address")) {
                config.getConfig("rpcSettings").getString("address")
            } else {
                getOptionalString("rpcAddress")
            }
        }

    /**
     * Returns the address of the web server that will connect to the node, or null if one hasn't been specified.
     */
    val webAddress: String?
        @Optional
        @Input
        get() = getOptionalString("webAddress")

    var configFile: String? = null
        @Optional @Input get
        private set

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    fun name(name: String) {
        this.name = name
        setValue("myLegalName", name)
    }

    /**
     * Set the Artemis P2P port for this node on localhost.
     *
     * @param p2pPort The Artemis messaging queue port.
     */
    fun p2pPort(p2pPort: Int) {
        p2pAddress(DEFAULT_HOST + ':'.toString() + p2pPort)
        this.p2pPort = p2pPort
    }

    /**
     * Set the Artemis P2P address for this node.
     *
     * @param p2pAddress The Artemis messaging queue host and port.
     */
    fun p2pAddress(p2pAddress: String) {
        setValue("p2pAddress", p2pAddress)
    }

    /**
     * Enable/disable the development mode
     *
     * @param devMode - true if devMode is enabled
     */
    fun devMode(devMode: Boolean?) {
        setValue("devMode", devMode)
    }

    /**
     * Set the Artemis RPC port for this node on localhost.
     *
     * @param rpcPort The Artemis RPC queue port.
     */
    @Deprecated("Use {@link CordformNode#rpcSettings(RpcSettings)} instead. Will be removed by Corda V5.0.")
    fun rpcPort(rpcPort: Int) {
        rpcAddress(DEFAULT_HOST + ':'.toString() + rpcPort)
        this.rpcPort = rpcPort
    }

    /**
     * Set the Artemis RPC address for this node.
     *
     * @param rpcAddress The Artemis RPC queue host and port.
     */
    @Deprecated("Use {@link CordformNode#rpcSettings(RpcSettings)} instead. . Will be removed by Corda V5.0.")
    fun rpcAddress(rpcAddress: String) {
        setValue("rpcAddress", rpcAddress)
    }

    /**
     * Configure a webserver to connect to the node via RPC. This port will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    fun webPort(webPort: Int) {
        webAddress(DEFAULT_HOST + ':'.toString() + webPort)
    }

    /**
     * Configure a webserver to connect to the node via RPC. This address will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    fun webAddress(webAddress: String) {
        setValue("webAddress", webAddress)
    }

    /**
     * Specifies RPC settings for the node.
     */
    fun rpcSettings(settings: RpcSettings) {
        config = settings.addTo("rpcSettings", config)
    }

    /**
     * Set the path to a file with optional properties, which are appended to the generated node.conf file.
     *
     * @param configFile The file path.
     */
    fun configFile(configFile: String) {
        this.configFile = configFile
    }

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    fun https(isHttps: Boolean) {
        config = config.withValue("useHTTPS", ConfigValueFactory.fromAnyRef(isHttps))
    }

    /**
     * Sets the H2 port for this node
     */
    fun h2Port(h2Port: Int) {
        config = config.withValue("h2port", ConfigValueFactory.fromAnyRef(h2Port))
    }

    fun useTestClock(useTestClock: Boolean) {
        config = config.withValue("useTestClock", ConfigValueFactory.fromAnyRef(useTestClock))
    }

    /**
     * Specifies RPC settings for the node.
     */
    fun rpcSettings(configureClosure: Closure<in RpcSettings>) {
        rpcSettings = project.configure(RpcSettings(), configureClosure) as RpcSettings
        config = rpcSettings.addTo("rpcSettings", config)
    }

    /**
     * Enables SSH access on given port
     *
     * @param sshdPort The port for SSH server to listen on
     */
    fun sshdPort(sshdPort: Int) {
        config = config.withValue("sshd.port", ConfigValueFactory.fromAnyRef(sshdPort))
    }

    fun sshdPort() {
        config.getInt("sshd.port")
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(coordinates, project), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(cordappProject), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project): Cordapp {
        return Cordapp(cordappProject).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String): Cordapp {
        return Cordapp(coordinates).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param configureFunc A lambda to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureFunc: Cordapp.() -> Unit): Cordapp {
        return Cordapp(coordinates).apply {
            configureFunc()
            internalCordapps += this
        }
    }

    fun cordapp(configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(project), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Configures the default cordapp automatically added to this node from this project
     *
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun projectCordapp(configureClosure: Closure<in Cordapp>): Cordapp {
        project.configure(projectCordapp, configureClosure) as Cordapp
        return projectCordapp
    }

    /**
     * The webserver JAR to be used by this node.
     *
     * If not provided, the default development webserver is used.
     *
     * @param webserverJar The file path of the webserver JAR to use.
     */
    fun webserverJar(webserverJar: String) {
        this.webserverJar = webserverJar
    }

    internal fun build() {
        if (config.hasPath("webAddress")) {
            installWebserverJar()
        }
        installAgentJar()
        installDrivers()
        installCordapps()
        installCordappConfigs()
        installConfig()
    }

    internal fun buildDocker() {
        installDrivers()
        installCordapps()
        installCordappConfigs()
    }

    internal fun installCordapps() {
        val cordappsDir = nodeDir.toPath().resolve("cordapps")
        val nodeCordapps = getCordappList().map(Node.ResolvedCordapp::jarFile).distinct()
        nodeCordapps.map { nodeCordapp ->
            project.copy {
                it.apply {
                    from(nodeCordapp)
                    into(cordappsDir)
                }
            }
        }
    }

    internal fun rootDir(rootDir: Path) {
        if (name == null) {
            project.logger.error("Node has a null name - cannot create node")
            throw IllegalStateException("Node has a null name - cannot create node")
        }
        // Parsing O= part directly because importing BouncyCastle provider in Cordformation causes problems
        // with loading our custom X509EdDSAEngine.
        val organizationName = name!!.trim().split(",").firstOrNull { it.startsWith("O=") }?.substringAfter("=")
        val dirName = organizationName ?: name
        containerName = dirName!!.replace("\\s++".toRegex(), "-").toLowerCase()
        this.rootDir = rootDir.toFile()
        nodeDir = File(this.rootDir, dirName.replace("\\s", ""))
        Files.createDirectories(nodeDir.toPath())
    }

    private fun configureProperties() {
        if (!rpcUsers.isEmpty()) {
            config = config.withValue("security", ConfigValueFactory.fromMap(mapOf(
                    "authService" to mapOf(
                            "dataSource" to mapOf(
                                    "type" to "INMEMORY",
                                    "users" to rpcUsers)))))
        }

        if (!notary.isEmpty()) {
            config = config.withValue("notary", ConfigValueFactory.fromMap(notary))
        }
        if (!extraConfig.isEmpty()) {
            config = config.withFallback(ConfigFactory.parseMap(extraConfig))
        }
        if (!config.hasPath("devMode")) {
            config = config.withValue("devMode", ConfigValueFactory.fromAnyRef(true))
        }

        if (flowOverrides.isNotEmpty()) {
            val mapToParse = mapOf("overrides" to
                    flowOverrides.map { pair -> mapOf("initiator" to pair.first, "responder" to pair.second) })
            config = config.withValue("flowOverrides", ConfigValueFactory.fromMap(mapToParse))
        }
    }

    /**
     * Installs the corda webserver JAR to the node directory
     */
    private fun installWebserverJar() {
        // If no webserver JAR is provided, the default development webserver is used.
        val webJar = if (webserverJar == null) {
            project.logger.lifecycle("Using default development webserver.")
            try {
                Cordformation.verifyAndGetRuntimeJar(project, "corda-testserver")
            } catch (e: IllegalStateException) {
                project.logger.lifecycle("Detecting older version of corda. Falling back to the old webserver.")
                Cordformation.verifyAndGetRuntimeJar(project, "corda-webserver")
            }
        } else {
            project.logger.lifecycle("Using custom webserver: $webserverJar.")
            File(webserverJar)
        }

        project.copy {
            it.apply {
                from(webJar)
                into(nodeDir)
                rename(webJar.name, webJarName)
            }
        }
    }

    fun runtimeVersion(): String {
        val releaseVersion = project.findRootProperty<String>("corda_release_version")
        val runtimeJarVersion = project.configuration("cordaRuntime").dependencies.filterNot { it.name.contains("web") }.singleOrNull()?.version
        if (releaseVersion == null && runtimeJarVersion == null) {
            throw IllegalStateException("Could not find a valid definition of corda version to use")
        } else {
            return listOfNotNull(runtimeJarVersion, releaseVersion).first()
        }
    }

    /**
     * Installs the jolokia monitoring agent JAR to the node/drivers directory
     */
    private fun installAgentJar() {
        // TODO: improve how we re-use existing declared external variables from root gradle.build
        val jolokiaVersion = project.findRootProperty("jolokia_version") ?: "1.6.0"

        val agentJar = project.configuration("runtime").files {
            (it.group == "org.jolokia") &&
                    (it.name == "jolokia-jvm") &&
                    (it.version == jolokiaVersion)
            // TODO: revisit when classifier attribute is added. eg && (it.classifier = "agent")
        }.firstOrNull()
        agentJar?.let {
            project.logger.info("Jolokia agent jar: $it")
            copyToDriversDir(it)
        }
    }

    internal fun installDrivers() {
        drivers?.let {
            project.logger.lifecycle("Copy $it to './drivers' directory")
            it.forEach { path -> copyToDriversDir(File(path)) }
        }
    }

    private fun copyToDriversDir(file: File) {
        if (file.isFile) {
            val driversDir = File(nodeDir, "drivers")
            project.copy {
                it.apply {
                    from(file)
                    into(driversDir)
                }
            }
        }
    }

    private fun createTempConfigFile(configObject: ConfigObject, fileNameTrail: String): File {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = configObject.render(options).split("\n").toList()
        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        val tmpDir = File(project.buildDir, "tmp")
        Files.createDirectories(tmpDir.toPath())
        val fileName = "${nodeDir.name}_$fileNameTrail"
        val tmpConfFile = File(tmpDir, fileName)
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)
        return tmpConfFile
    }

    private fun installCordappConfigs() {
        val cordappsDir = nodeDir.toPath().resolve("cordapps")
        val cordapps = getCordappList()
        val configDir = project.file(cordappsDir.resolve("config")).toPath()
        Files.createDirectories(configDir)
        for ((jarFile, config) in cordapps) {
            if (config == null) continue
            val configFile = configDir.resolve("${removeExtension(jarFile.fileName.toString())}.conf")
            Files.write(configFile, config.toByteArray())
        }
    }

    /**
     * Installs the configuration file to the root directory and detokenises it.
     */
    internal fun installConfig() {
        configureProperties()
        createNodeAndWebServerConfigFiles(config)
    }

    /**
     * Installs the Dockerized configuration file to the root directory and detokenises it.
     */
    internal fun installDockerConfig(defaultSsh: Int) {
        configureProperties()
        if (!config.hasPath("sshd.port")) {
            sshdPort(defaultSsh)
        }
        val dockerConf = config
                .withValue("p2pAddress", ConfigValueFactory.fromAnyRef("$containerName:$p2pPort"))
                .withValue("rpcSettings.address", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.port}"))
                .withValue("rpcSettings.adminAddress", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.adminPort}"))
                .withValue("detectPublicIp", ConfigValueFactory.fromAnyRef(false))
                .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef("jdbc:h2:file:./persistence/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000"))

        config = dockerConf
        createNodeAndWebServerConfigFiles(config)
    }

    private fun createNodeAndWebServerConfigFiles(config: Config) {
        val tmpConfFile = createTempConfigFile(createNodeConfig(config).root(), "node.conf")
        appendOptionalConfig(tmpConfFile)
        project.copy {
            it.apply {
                from(tmpConfFile)
                into(rootDir)
            }
        }
        if (config.hasPath("webAddress")) {
            val webServerConfigFile = createTempConfigFile(createWebserverConfig().root(), "web-server.conf")
            project.copy {
                it.apply {
                    from(webServerConfigFile)
                    into(rootDir)
                }
            }
        }
    }

    private fun createNodeConfig(configToUse: Config? = null) = configToUse?.withoutPath("webAddress")?.withoutPath("useHTTPS")
            ?: config.withoutPath("webAddress").withoutPath("useHTTPS")

    private fun createWebserverConfig(): Config {
        val webConfig = config.copyKeysTo(ConfigFactory.empty(),
                listOf("webAddress", "myLegalName", "security", "useHTTPS", "baseDirectory",
                        "keyStorePassword", "trustStorePassword", "exportJMXto", "custom", "devMode")
        )

        return when {
            config.hasPath("rpcSettings.address") -> config.copyTo("rpcAddress", webConfig, "rpcSettings.address")
            config.hasPath("rpcAddress") -> config.copyTo("rpcAddress", webConfig)
            else -> webConfig
        }
    }

    /**
     * Appends installed config file with properties from an optional file.
     */
    private fun appendOptionalConfig(confFile: File) {
        val optionalConfig: File? = when {
            project.findProperty(configFileProperty) != null -> //provided by -PconfigFile command line property when running Gradle task
                File(project.findProperty(configFileProperty) as String)
            configFile != null -> File(configFile)
            else -> null
        }

        if (optionalConfig != null) {
            if (!optionalConfig.exists()) {
                project.logger.error("$configFileProperty '$optionalConfig' not found")
            } else {
                confFile.appendBytes(optionalConfig.readBytes())
            }
        }
    }

    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    @Internal
    internal fun getCordappList(): List<ResolvedCordapp> {
        return internalCordapps.mapNotNull(::resolveCordapp).let {
            if (projectCordapp.deploy) (it + resolveBuiltCordapp()) else it
        }
    }

    private fun resolveCordapp(cordapp: Cordapp): ResolvedCordapp? {
        if (!cordapp.deploy) {
            return null
        }

        val cordappConfiguration = project.configuration("cordapp")
        val cordappName = if (cordapp.project != null) cordapp.project.name else cordapp.coordinates
        val cordappFile = cordappConfiguration.files {
            when {
                (it is ProjectDependency) && (cordapp.project != null) -> it.dependencyProject == cordapp.project
                cordapp.coordinates != null -> {
                    // Cordapps can sometimes contain a GString instance which fails the equality test with the Java string
                    @Suppress("RemoveRedundantCallsOfConversionMethods")
                    val coordinates = cordapp.coordinates.toString()
                    coordinates == (it.group + ":" + it.name + ":" + it.version)
                }
                else -> false
            }
        }

        return when {
            cordappFile.size == 0 -> throw GradleException("Cordapp $cordappName not found in cordapps configuration.")
            cordappFile.size > 1 -> throw GradleException("Multiple files found for $cordappName")
            else -> ResolvedCordapp(cordappFile.single().toPath(), cordapp.config)
        }
    }

    private fun resolveBuiltCordapp(): ResolvedCordapp {
        val projectCordappFile = project.tasks.getByName("jar").outputs.files.singleFile.toPath()
        return ResolvedCordapp(projectCordappFile, projectCordapp.config)
    }

    private fun getOptionalString(path: String): String? {
        return if (config.hasPath(path)) config.getString(path) else null
    }

    private fun setValue(path: String, value: Any?) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }

    fun flowOverride(initiator: String, responder: String) {
        flowOverrides.add(initiator to responder)
    }

    val flowOverrides: MutableList<Pair<String, String>> = mutableListOf()
}
