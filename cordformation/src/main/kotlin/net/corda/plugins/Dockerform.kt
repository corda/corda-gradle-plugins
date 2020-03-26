package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.artifacts.ModuleDependency
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

        private const val DEFAULT_DB_INIT_FILE = "init.sql";
        private const val DEFAULT_DB_STARTING_PORT = 5432
        private const val DEFAULT_DB_GRADLE_PROPERTY = "postgres_driver_version"
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

    private fun retrieveDefaultDbDriverDependency(driverGroup: String, driverName: String, driverVersion: Any?): File? {
        project.configuration("default").incoming.beforeResolve {
            project.dependencies.add("default", "org.postgresql:postgresql:$driverVersion")
        }
        return project.configuration("default").files {
            (it.group == driverGroup) && (it.name == driverName) && (it.version == driverVersion)
        }.firstOrNull()
    }

    private fun createDefaultDBConfig(port: Int, address: String): Config {
        return ConfigFactory.empty()
                .withValue("dataSourceProperties.dataSourceClassName", ConfigValueFactory.fromAnyRef("org.postgresql.ds.PGSimpleDataSource"))
                .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef("jdbc:postgresql://$address:$port/mydb?currentSchema=myschema"))
                .withValue("dataSourceProperties.dataSource.user", ConfigValueFactory.fromAnyRef("myuser"))
                .withValue("dataSourceProperties.dataSource.password", ConfigValueFactory.fromAnyRef("mypassword"))
                .withValue("database.transactionIsolationLevel", ConfigValueFactory.fromAnyRef("READ_COMMITTED"))
                .withValue("database.runMigration", ConfigValueFactory.fromAnyRef(true))
                .withValue("database.schema", ConfigValueFactory.fromAnyRef("myschema"))
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

        var services = mutableMapOf<String, Map<String, Any>>()

        nodes.forEachIndexed {index, it ->

            val nodeBuildDir = directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString()

            var service = mutableMapOf(
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

                it.dbSettings(DEFAULT_DB_STARTING_PORT + index, "${it.containerName}-db")
                it.installDefaultDatabaseConfig(createDefaultDBConfig(it.dbSettings.port, it.dbSettings.host), DEFAULT_DB_INIT_FILE)

                services[it.dbSettings.host] = mapOf(
                        "image" to "postgres",
                        "restart" to "unless-stopped",
                        "ports" to listOf(it.dbSettings.port),
                        "environment" to mapOf(
                                "POSTGRES_USER" to "myuser",
                                "POSTGRES_PASSWORD" to "mypassword",
                                "POSTGRES_DB" to "mydb",
                                "PGPORT" to it.dbSettings.port
                        ),
                        "volumes" to listOf(
                                "$nodeBuildDir/$DEFAULT_DB_INIT_FILE:/docker-entrypoint-initdb.d/$DEFAULT_DB_INIT_FILE"
                        )
                )

                service["image"] = it.dockerImage ?: "entdocker.software.r3.com/corda-enterprise-java1.8-${it.runtimeVersion().toLowerCase()}"
                service["depends_on"] = listOf(it.dbSettings.host)
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