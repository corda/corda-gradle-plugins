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
import java.io.File
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

        private const val DEFAULT_DB_GRADLE_PROPERTY = "postgres_driver_version"

        private const val DEFAULT_DB = "postgres"

        private const val DEFAULT_DB_INIT_FILE = "Postgres_init.sh"
        private const val DEFAULT_DB_DOCKERFILE = "Postgres_Dockerfile"
        private const val DEFAULT_DB_STARTING_PORT = 5432

        private const val DEFAULT_DB_USER = "myuser"
        private const val DEFAULT_DB_PASSWORD = "mypassword"
        private const val DEFAULT_DB_SCHEMA = "myschema"
        private const val DEFAULT_DB_NAME = "mydb"
        private const val DEFAULT_DB_TRANSACTION_ISOLATION_LEVEL = "READ_COMMITTED"
        private const val DEFAULT_DB_DATA_SOURCE_CLASS_NAME = "org.postgresql.ds.PGSimpleDataSource"
        private const val DEFAULT_DB_DRIVER_PATH = "jdbc:postgresql://"
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

    private fun createDefaultDbURL(dbType: String, dbDriverPath: String, dbHost: String, dbPort: Int, dbName: String, dbSchema: String): String {
        return when (dbType) {
            "postgres" -> "$dbDriverPath$dbHost:$dbPort/$dbName?currentSchema=$dbSchema"
            else -> "$dbType not currently supported"
        }
    }

    private fun retrieveDefaultDbDriverDependency(driverGroup: String, driverName: String, driverVersion: Any?): File? {
        return project.configuration("cordaDriver").files {
            (it.group == driverGroup) && (it.name == driverName) && (it.version == driverVersion)
        }.firstOrNull()
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

            if (it.drivers.isNullOrEmpty() && project.hasProperty(DEFAULT_DB_GRADLE_PROPERTY)) {

                val driverVersion = project.property(DEFAULT_DB_GRADLE_PROPERTY)
                val driverJar = retrieveDefaultDbDriverDependency(
                        "org.postgresql", "postgresql", driverVersion)
                if (driverJar != null) {
                    it.drivers = listOf(driverJar.path)
                    it.installDrivers()
                }

                it.installResource(DEFAULT_DB_INIT_FILE)
                it.installResource(DEFAULT_DB_DOCKERFILE)

                val dbPort = DEFAULT_DB_STARTING_PORT + index
                val dbHost = "${it.containerName}-db"
                val dbUrl = createDefaultDbURL(DEFAULT_DB, DEFAULT_DB_DRIVER_PATH, dbHost, dbPort, DEFAULT_DB_NAME, DEFAULT_DB_SCHEMA)

                val config = ConfigFactory.empty()
                        .withValue("dataSourceProperties.dataSourceClassName", ConfigValueFactory.fromAnyRef(DEFAULT_DB_DATA_SOURCE_CLASS_NAME))
                        .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef(dbUrl))
                        .withValue("dataSourceProperties.dataSource.user", ConfigValueFactory.fromAnyRef(DEFAULT_DB_USER))
                        .withValue("dataSourceProperties.dataSource.password", ConfigValueFactory.fromAnyRef(DEFAULT_DB_PASSWORD))
                        .withValue("database.transactionIsolationLevel", ConfigValueFactory.fromAnyRef(DEFAULT_DB_TRANSACTION_ISOLATION_LEVEL))
                        .withValue("database.runMigration", ConfigValueFactory.fromAnyRef(true))
                        .withValue("database.schema", ConfigValueFactory.fromAnyRef(DEFAULT_DB_SCHEMA))

                it.installDefaultDatabaseConfig(config)

                services[dbHost] = mapOf(
                        "build" to mapOf(
                             "context" to "$nodeBuildDir/",
                             "dockerfile" to DEFAULT_DB_DOCKERFILE,
                             "args" to mapOf(
                                "DB_NAME" to DEFAULT_DB_NAME,
                                "DB_SCHEMA" to DEFAULT_DB_SCHEMA,
                                "DB_USER" to DEFAULT_DB_USER,
                                "DB_PASSWORD" to DEFAULT_DB_PASSWORD,
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