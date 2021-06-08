package net.corda.plugins

import com.typesafe.config.*
import net.corda.plugins.Cordformation.Companion.CORDA_CPK_CONFIGURATION_NAME
import net.corda.plugins.Cordformation.Companion.CORDA_DRIVER_CONFIGURATION_NAME
import net.corda.plugins.Cordformation.Companion.CORDA_RUNTIME_ONLY_CONFIGURATION_NAME
import net.corda.plugins.Cordformation.Companion.CPK_CLASSIFIER
import net.corda.plugins.Cordformation.Companion.DEPLOY_CORDAPP_CONFIGURATION_NAME
import net.corda.plugins.Cordformation.Companion.DEFAULT_JOLOKIA_VERSION
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections.unmodifiableList
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Represents a node that will be installed.
 */
@Suppress("unused", "UnstableApiUsage")
open class Node @Inject constructor(
    private val objects: ObjectFactory,
    private val fs: FileSystemOperations,
    private val providers: ProviderFactory,
    private val task: Baseform
) {
    internal data class ResolvedCordapp(val jarFile: Path, val config: String?)

    private companion object {
        private const val webJarName = "corda-testserver.jar"
        private const val configFileProperty = "configFile"
        private const val DEFAULT_HOST = "localhost"
        private const val LOGS_DIR_NAME = "logs"
        private const val CPK_TASK_NAME = "cpk"

        /**
         * [ObjectFactory][org.gradle.api.model.ObjectFactory] refuses
         * to inject `null`, and so we need to inject a mock [Project]
         * object instead for those cases.
         */
        private val NO_PROJECT = cordaMock<Project>()
    }

    private val internalCordapps = mutableListOf<Cordapp>()

    /**
     * The list of CorDapps to install to the plugins directory.
     */
    val cordapps: List<Cordapp>
        @Nested
        get() = unmodifiableList(internalCordapps)

    @get:Nested
    val projectCordapp: Cordapp = objects.newInstance(Cordapp::class.java, "", task.project)
    internal lateinit var nodeDir: File
        @Internal get
        private set
    private lateinit var rootDir: File
    internal lateinit var containerName: String
        @Internal get
        private set
    private val rpcSettings: RpcSettings = objects.newInstance(RpcSettings::class.java)
    private var webserverJar: String? = null
    private var p2pPort = 10002
    @get:Input
    val rpcPort: Provider<Int> = objects.property(Int::class.java).apply {
        set(providers.provider { rpcSettings.port })
    }

    internal var config = ConfigFactory.empty()
        @Internal get
        private set

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

    val configFile: Property<String> = objects.property(String::class.java)
        @Optional @Input get

    // Should the cordform set up run data base migration scripts after installation - defaults to false for compatibility
    // with current and previous Corda versions
    @get:Input
    var runSchemaMigration: Boolean = false

    // If generating schemas, should app schema be generated using hibernate if missing migration scripts
    @get:Input
    var allowHibernateToManageAppSchema: Boolean = false

    //Configure the timeout for schema generation runtime
    @get:Internal
    var nodeJobTimeOutInMinutes: Long = 3

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
        p2pAddress("$DEFAULT_HOST:$p2pPort")
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
     * Configure a webserver to connect to the node via RPC. This port will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    fun webPort(webPort: Int) {
        webAddress("$DEFAULT_HOST:$webPort")
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
        this.configFile.set(configFile)
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
    fun rpcSettings(action: Action<in RpcSettings>) {
        action.execute(rpcSettings)
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

    @get:Internal
    internal var usesDefaultSSHPort: Boolean = false
        private set

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @param action An [Action] to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, action: Action<in Cordapp>): Cordapp {
        return objects.newInstance(Cordapp::class.java, coordinates.trim(), NO_PROJECT).also { cordapp ->
            action.execute(cordapp)
            internalCordapps += cordapp
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @param action An [Action] to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project, action: Action<in Cordapp>): Cordapp {
        return objects.newInstance(Cordapp::class.java, "", cordappProject).also { cordapp ->
            action.execute(cordapp)
            internalCordapps += cordapp
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project): Cordapp {
        return objects.newInstance(Cordapp::class.java, "", cordappProject).also { cordapp ->
            internalCordapps += cordapp
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String): Cordapp {
        return objects.newInstance(Cordapp::class.java, coordinates.trim(), NO_PROJECT).also { cordapp ->
            internalCordapps += cordapp
        }
    }

    /**
     * Configures the default cordapp automatically added to this node from this project.
     *
     * @param action A lambda to configure the default [Cordapp].
     * @return The default [Cordapp].
     */
    fun cordapp(action: Action<in Cordapp>): Cordapp {
        return projectCordapp(action)
    }

    /**
     * Configures the default cordapp automatically added to this node from this project.
     *
     * @param action An [Action] to configure the default [Cordapp].
     * @return The default [Cordapp].
     */
    fun projectCordapp(action: Action<in Cordapp>): Cordapp {
        action.execute(projectCordapp)
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
        runSchemaMigration()
    }

    internal fun buildDocker() {
        installDrivers()
        installCordapps()
        installCordappConfigs()
        runSchemaMigration()
    }

    internal fun installCordapps() {
        val cordappsDir = nodeDir.toPath().resolve("cordapps")
        val nodeCordapps = getCordappList().map(ResolvedCordapp::jarFile).distinct()
        nodeCordapps.map { nodeCordapp ->
            fs.copy {
                it.apply {
                    from(nodeCordapp)
                    into(cordappsDir)
                }
            }
        }
    }

    internal fun rootDir(rootDir: File) {
        if (name == null) {
            task.logger.error("Node has a null name - cannot create node")
            throw IllegalStateException("Node has a null name - cannot create node")
        }
        // Parsing O= & OU= part directly because importing BouncyCastle provider in Cordformation causes problems
        // with loading our custom X509EdDSAEngine.
        val attributes = name!!.trim().split(",").map(String::trim)
        val organizationName = attributes.find { it.startsWith("O=") }?.substringAfter('=')
        val organizationUnit = attributes.find { it.startsWith("OU=") }?.substringAfter('=')
        val dirName = when {
            organizationName.isNullOrBlank() -> name
            organizationUnit.isNullOrBlank() -> organizationName
            else -> organizationName + '_' + organizationUnit
        }

        containerName = dirName!!.replace("\\s++".toRegex(), "-").toLowerCase()
        this.rootDir = rootDir
        nodeDir = File(rootDir, dirName.replace("\\s++".toRegex(), ""))
        Files.createDirectories(nodeDir.toPath())
    }

    private fun configureProperties() {
        if (rpcUsers.isNotEmpty()) {
            config = config.withValue("security", ConfigValueFactory.fromMap(mapOf(
                    "authService" to mapOf(
                            "dataSource" to mapOf(
                                    "type" to "INMEMORY",
                                    "users" to rpcUsers)))))
        }

        if (notary.isNotEmpty()) {
            config = config.withValue("notary", ConfigValueFactory.fromMap(notary))
        }
        if (extraConfig.isNotEmpty()) {
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
        val webJar = webserverJar?.let { web ->
            task.logger.lifecycle("Using custom webserver: $web.")
            File(web)
        } ?: run {
            // If no webserver JAR is provided, the default development webserver is used.
            task.logger.lifecycle("Using default development webserver.")
            try {
                Cordformation.verifyAndGetRuntimeJar(task.project, "corda-testserver")
            } catch (e: IllegalStateException) {
                task.logger.lifecycle("Detecting older version of corda. Falling back to the old webserver.")
                Cordformation.verifyAndGetRuntimeJar(task.project, "corda-webserver")
            }
        }

        fs.copy {
            it.apply {
                from(webJar)
                into(nodeDir)
                rename(webJar.name, webJarName)
            }
        }
    }


    private fun createSchemasCmd() = listOfNotNull(
            Paths.get(providers.systemProperty("java.home").get(), "bin", "java").toString(),
            "-jar",
            "corda.jar",
            "run-migration-scripts",
            "--core-schemas",
            "--app-schemas",
            if (allowHibernateToManageAppSchema) "--allow-hibernate-to-manage-app-schema" else null)

    private fun runSchemaMigration(){
        if (!runSchemaMigration) return
        task.logger.lifecycle("Run database schema migration scripts${if(allowHibernateToManageAppSchema) " - managing CorDapp schemas with hibernate" else ""}")
        runNodeJob(createSchemasCmd(), "node-schema-cordform.log")
    }

    @Suppress("SameParameterValue")
    private fun runNodeJob(command: List<String>, logfileName: String) {
        val logsDir = Files.createDirectories(nodeDir.toPath().resolve(LOGS_DIR_NAME))
        val nodeRedirectFile = logsDir.resolve(logfileName).toFile()
        val process = ProcessBuilder(command)
                .directory(nodeDir)
                .redirectErrorStream(true)
                .redirectOutput(nodeRedirectFile)
                .apply { environment()["CAPSULE_CACHE_DIR"] = "../.cache" }
                .start()
        try {
            if (!process.waitFor(nodeJobTimeOutInMinutes, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                printNodeOutputAndThrow(nodeRedirectFile)
            }
            if (process.exitValue() != 0) printNodeOutputAndThrow(nodeRedirectFile)
        } catch (e: InterruptedException) {
            // Don't leave this process dangling if the thread is interrupted.
            process.destroyForcibly()
            throw e
        }
    }

    private fun printNodeOutputAndThrow(stdoutFile: File): Nothing {
        task.logger.error("#### Error while generating node info file $name ####")
        task.logger.error(stdoutFile.readText())
        throw IllegalStateException("Error while generating node info file. Please check the logs in ${stdoutFile.parent}.")
    }

    fun runtimeVersion(): String {
        val project = task.project
        val releaseVersion = project.findRootProperty("corda_release_version")
        val runtimeJarVersion = project.configuration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME).dependencies.filterNot { it.name.contains("web") }.singleOrNull()?.version
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
        val jolokiaVersion = task.project.findRootProperty("jolokia_version") ?: DEFAULT_JOLOKIA_VERSION

        val agentJar = task.project.configuration(RUNTIME_CLASSPATH_CONFIGURATION_NAME).files {
            (it.group == "org.jolokia") &&
                    (it.name == "jolokia-jvm") &&
                    (it.version == jolokiaVersion)
            // TODO: revisit when classifier attribute is added. eg && (it.classifier = "agent")
        }.firstOrNull()
        agentJar?.let {
            task.logger.info("Jolokia agent jar: $it")
            copyToDriversDir(it)
        }
    }

    internal fun installDrivers() {
        task.project.configuration(CORDA_DRIVER_CONFIGURATION_NAME).files.forEach {
            task.logger.lifecycle("Copy ${it.name} to './drivers' directory")
            copyToDriversDir(it)
        }

        drivers?.let {
            task.logger.lifecycle("Copy $it to './drivers' directory")
            it.forEach { path -> copyToDriversDir(File(path)) }
        }
    }

    private fun copyToDriversDir(file: File) {
        if (file.isFile) {
            val driversDir = File(nodeDir, "drivers")
            fs.copy {
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
        // Need to write a temporary file first to use the fs.copy, which resolves directories correctly.
        val fileName = "${nodeDir.name}_$fileNameTrail"
        val tmpConfFile = File(task.temporaryDir, fileName)
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)
        return tmpConfFile
    }

    private fun installCordappConfigs() {
        val cordappsDir = nodeDir.toPath().resolve("cordapps")
        val cordapps = getCordappList()
        val configDir = cordappsDir.resolve("config")
        for ((jarFile, config) in cordapps) {
            if (config == null) continue
            val fileNameWithoutExtension = jarFile.fileName.toString().let {
                when(val dotIndex = it.lastIndexOf('.')) {
                    -1 -> it
                    else -> it.substring(0, dotIndex)
                }
            }
            val configFile = configDir.resolve("${fileNameWithoutExtension}.conf")
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
    internal fun installDockerConfig(defaultSSHPort: Int) {
        configureProperties()

        if (!config.hasPath("sshd.port")) {
            sshdPort(defaultSSHPort)
            usesDefaultSSHPort = true
        }

        val configDefaults = ConfigFactory.empty()
                .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef("jdbc:h2:file:./persistence/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000"))
        val dockerConf = config
                .withValue("p2pAddress", ConfigValueFactory.fromAnyRef("$containerName:$p2pPort"))
                .withValue("rpcSettings.address", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.port}"))
                .withValue("rpcSettings.adminAddress", ConfigValueFactory.fromAnyRef("$containerName:${rpcSettings.adminPort}"))
                .withValue("detectPublicIp", ConfigValueFactory.fromAnyRef(false))
                .withFallback(configDefaults)

        config = dockerConf
        createNodeAndWebServerConfigFiles(config)
    }

     /**
     * Installs the default database (part of EG-117 Epic)
     */
    internal fun installDefaultDatabaseConfig(dbConfig: Config) {
        val dockerConf = dbConfig.withFallback(config).resolve()
        config = dockerConf
        updateNodeConfigFile(config)
    }

    private fun updateNodeConfigFile(config: Config) {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = createNodeConfig(config).root().render(options).split("\n").toList()
        val nodeConfFile = File(nodeDir, "node.conf")
        Files.write(nodeConfFile.toPath(), configFileText)
    }

    private fun createNodeAndWebServerConfigFiles(config: Config) {
        val tmpConfFile = createTempConfigFile(createNodeConfig(config).root(), "node.conf")
        appendOptionalConfig(tmpConfFile)
        fs.copy {
            it.apply {
                from(tmpConfFile)
                into(rootDir)
            }
        }
        if (config.hasPath("webAddress")) {
            val webServerConfigFile = createTempConfigFile(createWebserverConfig().root(), "web-server.conf")
            fs.copy {
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
        //provided by -PconfigFile command line property when running Gradle task
        val optionalConfig = providers.gradleProperty(configFileProperty)
            .orElse(configFile)
            .map { File(it.toString()) }
            .orNull

        if (optionalConfig != null) {
            if (!optionalConfig.exists()) {
                task.logger.error("$configFileProperty '$optionalConfig' not found")
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
        return internalCordapps.mapNotNullTo(LinkedList(), ::resolveCordapp).also { resolved ->
            resolveBuiltCordapp()?.also { resolved.add(it) }
        }
    }

    private fun resolveCordapp(cordapp: Cordapp): ResolvedCordapp? {
        if (!cordapp.deploy) {
            return null
        }

        val cordappConfiguration = task.project.configuration(DEPLOY_CORDAPP_CONFIGURATION_NAME).resolvedConfiguration
        val cordappFile = cordappConfiguration.firstLevelModuleDependencies.flatMapTo(LinkedHashSet<File>()) { dep ->
            when {
                cordapp.projectPath != null ->
                    if (dep.configuration == CORDA_CPK_CONFIGURATION_NAME) {
                        dep.moduleArtifacts.filter { artifact ->
                            val componentId = artifact.id.componentIdentifier
                            componentId is ProjectComponentIdentifier && componentId.projectPath == cordapp.projectPath
                        }.map(ResolvedArtifact::getFile)
                    } else {
                        emptyList()
                    }
                cordapp.coordinates.isNotEmpty() -> {
                    val moduleCoordinates = with(dep) {
                        "$moduleGroup:$moduleName:$moduleVersion"
                    }
                    if (cordapp.coordinates == moduleCoordinates) {
                        dep.moduleArtifacts.filter { artifact ->
                            artifact.classifier == CPK_CLASSIFIER
                        }.map(ResolvedArtifact::getFile)
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }

        val cordappName = cordapp.projectPath ?: cordapp.coordinates
        return when {
            cordappFile.isEmpty() -> throw GradleException("CorDapp $cordappName not found in cordapp configuration.")
            cordappFile.size > 1 -> throw GradleException("Multiple files found for $cordappName")
            else -> ResolvedCordapp(cordappFile.single().toPath(), cordapp.config)
        }
    }

    private fun resolveBuiltCordapp(): ResolvedCordapp? {
        val cpks = projectCordapp.project.configurations.findByName(CORDA_CPK_CONFIGURATION_NAME) ?: return null
        val cpkFile = cpks.artifacts.files.singleFile.toPath()
        return if (projectCordapp.deploy) {
            ResolvedCordapp(cpkFile, projectCordapp.config)
        } else {
            null
        }
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

    @get:Input
    val flowOverrides: MutableList<Pair<String, String>> = mutableListOf()
}
