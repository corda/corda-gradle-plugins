package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject


/**
 * Creates docker-compose file and image definitions based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Dockerform @Inject constructor(objects: ObjectFactory) : Baseform(objects) {

    private companion object {

        private const val DEFAULT_DB_STARTING_PORT = 5432
        private const val DEFAULT_SSH_PORT = 22022
        private val DEFAULT_DIRECTORY: Path = Paths.get("build", "docker")
        private const val COMPOSE_SPEC_VERSION = "3"

        private val YAML_FORMAT_OPTIONS = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }

        private val YAML_MAPPER = Yaml(YAML_FORMAT_OPTIONS)
    }

    init {
        description = "Creates a docker-compose file and image definitions for a deployment of Corda Nodes."
    }

    private val directoryPath: Path = project.projectDir.toPath().resolve(directory)

    val dockerComposePath: Path
        @PathSensitive(RELATIVE)
        @InputFile
        get() {
            val wantedPath = directoryPath.resolve("docker-compose.yml")
            if (!Files.exists(wantedPath)) {
                Files.createDirectories(wantedPath.parent)
                Files.createFile(wantedPath)
            }
            return wantedPath
        }

    @get:Optional
    @get:Input
    var dockerConfig: Map<String, Any> = emptyMap()

    /**
     * Docker image for this node, or null if one hasn't been specified.
     */
    @get:Optional
    @get:Input
    var dockerImage: String? = null

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running DockerForm task")
        initializeConfiguration()
        nodes.forEach { it.installDockerConfig(DEFAULT_SSH_PORT) }
        installCordaJar()
        generateKeystoreAndSignCordappJar()
        generateExcludedWhitelist()
        bootstrapNetwork()
        nodes.forEach(Node::buildDocker)

        val services = mutableMapOf<String, MutableMap<String, Any>>()
        val volumes = mutableMapOf<String, Map<String, Any>>()

        nodes.forEachIndexed {index, it ->

            val nodeBuildDir = directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString()

            val service = mutableMapOf(
                    "volumes" to listOf(
                            "$nodeBuildDir/node.conf:/etc/corda/node.conf",
                            "$nodeBuildDir/certificates:/opt/corda/certificates",
                            "$nodeBuildDir/logs:/opt/corda/logs",
                            "$nodeBuildDir/persistence:/opt/corda/persistence",
                            "$nodeBuildDir/cordapps:/opt/corda/cordapps",
                            "$nodeBuildDir/network-parameters:/opt/corda/network-parameters",
                            "$nodeBuildDir/additional-node-infos:/opt/corda/additional-node-infos",
                            "$nodeBuildDir/drivers:/opt/corda/drivers"
                    ),
                    "ports" to listOf(it.rpcPort, it.config.getInt("sshd.port")),
                    "image" to (dockerImage ?: "corda/corda-zulu-${it.runtimeVersion().toLowerCase()}")
            )

            if (dockerConfig.isNotEmpty()) {

                val dockerConfig = ConfigFactory.parseMap(dockerConfig)

                val dbDockerfile = dockerConfig.getString("dockerConfig.dbDockerfile")
                val dbDockerfileArgs = dockerConfig.getConfig("dockerConfig.dbDockerfileArgs")
                val dbUser = dockerConfig.getString("dockerConfig.dbUser")
                val dbPassword = dockerConfig.getString("dockerConfig.dbPassword")
                val dbDataSourceClassName = dockerConfig.getString("dataSourceProperties.dataSourceClassName")
                val dbTransactionIsolationLevel = dockerConfig.getString("database.transactionIsolationLevel")
                val dbRunMigration = dockerConfig.getBoolean("database.runMigration")
                val dbSchema= dockerConfig.getString("dockerConfig.dbSchema")
                val persistent = dockerConfig.getBoolean("dockerConfig.persistent")

                // These are allocated by docker-compose
                val dbPort = DEFAULT_DB_STARTING_PORT + index
                val dbHost = "${it.containerName}-db"
                val defaultUrlArgs = ConfigFactory.empty()
                        .withValue("DBHOSTNAME",ConfigValueFactory.fromAnyRef(dbHost))
                        .withValue("DBPORT",ConfigValueFactory.fromAnyRef(dbPort))

                // Override the port and hostname
                val dockerfileArgs = defaultUrlArgs.withFallback(dbDockerfileArgs).resolve()

                var dbUrl = dockerConfig.getString("dataSourceProperties.dataSource.url")

                if (dockerConfig.hasPath("dataSourceProperties.dataSource.urlArgs")) {
                    val urlArgs = defaultUrlArgs.withFallback(
                            dockerConfig.getConfig("dataSourceProperties.dataSource.urlArgs")).resolve()

                    val dbUrlConfig = ConfigFactory.parseString("DBURL=${TypesafeUtils.encodeString(dbUrl)}")
                            .resolveWith(urlArgs)

                    dbUrl = dbUrlConfig.getString("DBURL")
                }

                val dbConfig = ConfigFactory.empty()
                        .withValue("dataSourceProperties.dataSourceClassName", ConfigValueFactory.fromAnyRef(dbDataSourceClassName))
                        .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef(dbUrl))
                        .withValue("dataSourceProperties.dataSource.user", ConfigValueFactory.fromAnyRef(dbUser))
                        .withValue("dataSourceProperties.dataSource.password", ConfigValueFactory.fromAnyRef(dbPassword))
                        .withValue("database.transactionIsolationLevel", ConfigValueFactory.fromAnyRef(dbTransactionIsolationLevel))
                        .withValue("database.runMigration", ConfigValueFactory.fromAnyRef(dbRunMigration))
                        .withValue("database.schema", ConfigValueFactory.fromAnyRef(dbSchema))

                it.installDefaultDatabaseConfig(dbConfig)

                val args = mutableMapOf<String, Any>()

                dockerfileArgs.entrySet().toList().map {
                    args[it.key] = it.value.unwrapped()
                }

                val database = mutableMapOf(
                        "build" to mapOf(
                             "context" to project.buildDir.absolutePath,
                             "dockerfile" to project.buildDir.resolve(dbDockerfile).toString(),
                             "args" to args
                        ),
                        "restart" to "unless-stopped",
                        "ports" to listOf(dbPort)
                )

                // attach database dependency to the node service
                service["image"] = dockerImage ?: "entdocker.software.r3.com/corda-enterprise-java1.8-${it.runtimeVersion().toLowerCase()}"
                service["depends_on"] = listOf(dbHost)

                // append persistence volume if it is required
                if (persistent) {
                    val volume = "$dbHost-volume"
                    val volumeDir = "$nodeBuildDir/data"
                    File(volumeDir).mkdirs()
                    volumes[volume] = mapOf(
                            "driver" to "local",
                            "driver_opts" to mapOf(
                                 "type" to "none",
                                 "device" to "$volumeDir",
                                 "o" to "bind"
                            )
                    )
                    database["volumes"] = listOf("$volume:/var/lib/postgresql/data:z")
                }
                services[dbHost] = database
            }

            services[it.containerName] = service
        }

        val dockerComposeObject = mutableMapOf(
                "version" to COMPOSE_SPEC_VERSION,
                "services" to services)

        if (volumes.isNotEmpty()) {
            dockerComposeObject["volumes"] = volumes
        }

        val dockerComposeContent = YAML_MAPPER.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray())
    }
}