package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.lang.StringBuilder
import java.nio.charset.StandardCharsets
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

        private const val DEFAULT_SSH_PORT = 22022
        private val DEFAULT_DIRECTORY: Path = Paths.get("build", "docker")
        private const val COMPOSE_SPEC_VERSION = "3"

        private val YAML_FORMAT_OPTIONS = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }

        private val YAML_MAPPER = Yaml(YAML_FORMAT_OPTIONS)

        private const val DEFAULT_DB_INIT_FILE = "Postgres_init.sh"
        private const val DEFAULT_DB_DOCKERFILE = "Postgres_Dockerfile"
        private const val DEFAULT_DB_STARTING_PORT = 5432
        private const val DEFAULT_DB_USER = "myuser"
        private const val DEFAULT_DB_PASSWORD = "mypassword"
        private const val DEFAULT_DB_SCHEMA = "myschema"
        private const val DEFAULT_DB_NAME = "mydb"
        private const val DEFAULT_DB_TRANSACTION_ISOLATION_LEVEL = "READ_COMMITTED"
        private const val DEFAULT_DB_DATA_SOURCE_CLASS_NAME = "org.postgresql.ds.PGSimpleDataSource"
        private const val DEFAULT_DB_RUN_MIGRATION = true
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
        bootstrapNetwork()
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
                    "image" to (it.dockerImage ?: "corda/corda-zulu-${it.runtimeVersion().toLowerCase()}")
            )

            if (it.dockerConfig.isNotEmpty()) {

                var dockerConfig = ConfigFactory.parseMap(it.dockerConfig)

                val dockerfile = when {
                    dockerConfig.hasPath("dbDockerConfig.dockerfile") -> dockerConfig.getString("dbDockerConfig.dockerfile")
                    else -> DEFAULT_DB_DOCKERFILE
                }
                val dbInit = when {
                    dockerConfig.hasPath("dbDockerConfig.dbInit") -> dockerConfig.getString("dbDockerConfig.dbInit")
                    else -> DEFAULT_DB_INIT_FILE
                }
                val dbName = when {
                    dockerConfig.hasPath("dbDockerConfig.dbName") -> dockerConfig.getString("dbDockerConfig.dbName")
                    else -> DEFAULT_DB_NAME
                }
                val dbSchema = when {
                    dockerConfig.hasPath("dbDockerConfig.dbSchema") -> dockerConfig.getString("dbDockerConfig.dbSchema")
                    else -> DEFAULT_DB_SCHEMA
                }
                val dbUser = when {
                    dockerConfig.hasPath("dbDockerConfig.dbUser") -> dockerConfig.getString("dbDockerConfig.dbUser")
                    else -> DEFAULT_DB_USER
                }
                val dbPassword = when {
                    dockerConfig.hasPath("dbDockerConfig.dbPassword") -> dockerConfig.getString("dbDockerConfig.dbPassword")
                    else -> DEFAULT_DB_PASSWORD
                }
                val dbDataSourceClassName = when {
                    dockerConfig.hasPath("dataSourceProperties.dataSourceClassName") -> dockerConfig.getString("dataSourceProperties.dataSourceClassName")
                    else -> DEFAULT_DB_DATA_SOURCE_CLASS_NAME
                }
                val dbTransactionIsolationLevel = when {
                    dockerConfig.hasPath("database.transactionIsolationLevel") -> dockerConfig.getString("database.transactionIsolationLevel")
                    else -> DEFAULT_DB_TRANSACTION_ISOLATION_LEVEL
                }
                val dbRunMigration = when {
                    dockerConfig.hasPath("database.runMigration") -> dockerConfig.getBoolean("database.runMigration")
                    else -> DEFAULT_DB_RUN_MIGRATION
                }

                val dbPort = DEFAULT_DB_STARTING_PORT + index
                val dbHost = "${it.containerName}-db"

                val dbUrl = when {
                    dockerConfig.hasPath("dataSourceProperties.dataSource.url") -> {
                        var url = dockerConfig.getString("dataSourceProperties.dataSource.url")
                        url.replace("\${DBHOSTNAME}", dbHost)
                           .replace("\${DBPORT}", dbPort.toString())
                           .replace("\${DBNAME}", dbName)
                           .replace("\${DBSCHEMA}", dbSchema)
                    }
                    else -> "jdbc:postgresql://${dbHost}:${dbPort}/${dbName}?currentSchema=${dbSchema}"
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
                it.installResource(dockerfile)
                it.installResource(dbInit)

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

                service["image"] = it.dockerImage ?: "entdocker.software.r3.com/corda-enterprise-java1.8-${it.runtimeVersion().toLowerCase()}"
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