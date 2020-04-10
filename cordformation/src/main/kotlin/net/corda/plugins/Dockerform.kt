package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.internal.impldep.org.eclipse.jgit.util.QuotedString
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileNotFoundException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
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

    private fun installResource(resourceName: String) {
        javaClass.getResourceAsStream(resourceName)?.use {
            input -> Files.copy(input, File(resourceName).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } ?: throw FileNotFoundException(resourceName)
    }

    /**
     * Returns the Docker image for this node, or null if one hasn't been specified.
     */
    @get:Optional
    @get:Input
    val dockerImage: String? = null

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running DockerForm task")
        initializeConfiguration()
        nodes.forEach { it -> it.installDockerConfig(DEFAULT_SSH_PORT) }
        installCordaJar()
        nodes.forEach(Node::installDrivers)
        generateKeystoreAndSignCordappJar()
        generateExcludedWhitelist()
        //bootstrapNetwork()
        nodes.forEach(Node::buildDocker)

        val services = mutableMapOf<String, Map<String, Any>>()

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

                var dockerConfig = ConfigFactory.parseMap(dockerConfig)

                val dockerfile = dockerConfig.getString("dbDockerConfig.dockerfile")
                val dbInit = dockerConfig.getString("dbDockerConfig.dbInit")
                val dbName = dockerConfig.getString("dbDockerConfig.dbName")
                val dbUser = dockerConfig.getString("dbDockerConfig.dbUser")
                val dbPassword = dockerConfig.getString("dbDockerConfig.dbPassword")
                val dbDataSourceClassName = dockerConfig.getString("dataSourceProperties.dataSourceClassName")
                val dbTransactionIsolationLevel = dockerConfig.getString("database.transactionIsolationLevel")
                val dbRunMigration = dockerConfig.getBoolean("database.runMigration")
                val dbSchema= dockerConfig.getString("dbDockerConfig.dbSchema")

                val dbPort = DEFAULT_DB_STARTING_PORT + index
                val dbHost = "${it.containerName}-db"

                val dbUrlConfig = ConfigFactory
                        .parseString("DBURL=${dockerConfig.getString("dataSourceProperties.dataSource.url")}")
                        .resolveWith(ConfigFactory.empty()
                                .withValue("DBHOSTNAME", ConfigValueFactory.fromAnyRef(dbHost))
                                .withValue("DBPORT", ConfigValueFactory.fromAnyRef(dbPort))
                                .withValue("DBNAME", ConfigValueFactory.fromAnyRef(dbName))
                                .withValue("DBSCHEMA", ConfigValueFactory.fromAnyRef(dbSchema)))

                val dbUrl = dbUrlConfig.getString("DBURL")

                val dbConfig = ConfigFactory.empty()
                        .withValue("dataSourceProperties.dataSourceClassName", ConfigValueFactory.fromAnyRef(dbDataSourceClassName))
                        .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef(dbUrl))
                        .withValue("dataSourceProperties.dataSource.user", ConfigValueFactory.fromAnyRef(dbUser))
                        .withValue("dataSourceProperties.dataSource.password", ConfigValueFactory.fromAnyRef(dbPassword))
                        .withValue("database.transactionIsolationLevel", ConfigValueFactory.fromAnyRef(dbTransactionIsolationLevel))
                        .withValue("database.runMigration", ConfigValueFactory.fromAnyRef(dbRunMigration))
                        .withValue("database.schema", ConfigValueFactory.fromAnyRef(dbSchema))

                it.installDefaultDatabaseConfig(dbConfig)
                installResource("$nodeBuildDir/$dockerfile")
                installResource("$nodeBuildDir/$dbInit")

                services[dbHost] = mapOf(
                        "build" to mapOf(
                             "context" to "$nodeBuildDir/",
                             "dockerfile" to dockerfile,
                             "args" to mapOf(
                                "DB_NAME" to dbName,
                                "DB_SCHEMA" to dbSchema,
                                "DB_USER" to dbUser,
                                "DB_PASSWORD" to dbPassword,
                                "DB_PORT" to dbPort
                             )
                        ),
                        "restart" to "unless-stopped",
                        "ports" to listOf(dbPort)
                )

                service["image"] = dockerImage ?: "entdocker.software.r3.com/corda-enterprise-java1.8-${it.runtimeVersion().toLowerCase()}"
                service["depends_on"] = listOf(dbHost)
            }

            services[it.containerName] = service
        }

        val dockerComposeObject = mapOf(
                "version" to COMPOSE_SPEC_VERSION,
                "services" to services)

        val dockerComposeContent = YAML_MAPPER.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray(StandardCharsets.UTF_8))
    }
}