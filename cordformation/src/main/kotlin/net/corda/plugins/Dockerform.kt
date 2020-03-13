package net.corda.plugins

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

        private const val POSTGRES_INIT_FILE = "init.sql";

        private const val STARTING_DB_PORT = 5432
        private const val STARTING_SSH_PORT = 22022

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

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running DockerForm task")
        initializeConfiguration()
        nodes.forEachIndexed {index, it -> it.installDockerConfig(STARTING_SSH_PORT + index) }
        installCordaJar()
        nodes.forEach(Node::installDrivers)
        generateKeystoreAndSignCordappJar()
        generateExcludedWhitelist()
        bootstrapNetwork()
        nodes.forEachIndexed { index, it -> it.installDatabaseConfig(STARTING_DB_PORT + index, "${it.containerName}-db", POSTGRES_INIT_FILE) }
        nodes.forEach(Node::buildDocker)

        // Transform nodes path the absolute ones
        val services = nodes.map {
            val nodeBuildDir = directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString()

            it.containerName to mapOf(
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
                    "ports" to listOf(it.rpcPort, it.config.getInt("sshd.port"), it.p2pPort),
                    "image" to "entdocker.software.r3.com/corda-enterprise-java1.8-${it.runtimeVersion().toLowerCase()}",
                    "depends_on" to listOf("${it.dbSettings.host}")
            );
        }.toMap().toMutableMap()

        var databases = mutableListOf<String>()

        nodes.forEach {
            val nodeBuildDir = directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString()

            val body = mapOf(
                    "image" to "postgres",
                    "restart" to "unless-stopped",
                    "ports" to listOf("${it.dbSettings.port}:${it.dbSettings.port}"),
                    "environment" to mapOf(
                            "POSTGRES_USER" to "myuser",
                            "POSTGRES_PASSWORD" to "mypassword",
                            "POSTGRES_DB" to "mydb",
                            "PGPORT" to it.dbSettings.port
                    ),
                    "volumes" to listOf(
                            "$nodeBuildDir/$POSTGRES_INIT_FILE:/docker-entrypoint-initdb.d/$POSTGRES_INIT_FILE"
                    )
            );
            services[it.dbSettings.host] = body
            databases.add(it.dbSettings.host)
        }

        services["adminer"] = mapOf(
                "image" to "adminer",
                "restart" to "unless-stopped",
                "depends_on" to databases,
                "ports" to listOf("8080:8080")
        )

        val dockerComposeObject = mapOf(
                "version" to COMPOSE_SPEC_VERSION,
                "services" to services)

        val dockerComposeContent = YAML_MAPPER.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray(StandardCharsets.UTF_8))
    }
}

