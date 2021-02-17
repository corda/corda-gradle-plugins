package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
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

        private val YAML_MAPPER = Yaml(DockerComposeRepresenter(), YAML_FORMAT_OPTIONS)
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
     * Base Docker image for each node
     */
    @get:Input
    val dockerImage: Property<String> = objects.property(String::class.java)

    /**
     * External service config
     */
    @get:Nested
    val external: External = objects.newInstance(External::class.java)

    fun external(action: Action<in External>) {
        action.execute(external)
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        logger.lifecycle("Running DockerForm task")
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

            val nodeBuildPath = directoryPath.resolve(it.nodeDir.name).toAbsolutePath()
            val nodeBuildDir = nodeBuildPath.toString()

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
                    "environment" to listOf("ACCEPT_LICENSE=\${ACCEPT_LICENSE}"),
                    "ports" to listOf(it.rpcPort.get(),
                            when (it.usesDefaultSSHPort) {
                               true -> it.config.getInt("sshd.port")
                               false -> QuotedString("${it.config.getInt("sshd.port")}:${it.config.getInt("sshd.port")}")
                            }),
                    "image" to dockerImage.get()
            )

            if (dockerConfig.isNotEmpty()) {

                val dockerConfig = ConfigFactory.parseMap(dockerConfig)

                // Generate port and hostname parameters to be used by docker-compose
                val dbPort = DEFAULT_DB_STARTING_PORT + index
                val dbHost = "${it.containerName}-db"
                val defaultUrlArgs = ConfigFactory.empty()
                        .withValue("DBHOSTNAME",ConfigValueFactory.fromAnyRef(dbHost))
                        .withValue("DBPORT",ConfigValueFactory.fromAnyRef(dbPort))

                var dbUrl = dockerConfig.getString("dataSourceProperties.dataSource.url")

                if (dockerConfig.hasPath("dataSourceProperties.dataSource.urlArgs")) {
                    val urlArgs = defaultUrlArgs.withFallback(
                            dockerConfig.getConfig("dataSourceProperties.dataSource.urlArgs")).resolve()

                    dbUrl = TypesafeUtils.resolveString(dbUrl, urlArgs)
                }

                // Install the database configuration
                val dbConfig = ConfigFactory.empty()
                        .withValue("dataSourceProperties.dataSource.url", ConfigValueFactory.fromAnyRef(dbUrl))
                        .withValue("dataSourceProperties.dataSourceClassName", dockerConfig.getValue("dataSourceProperties.dataSourceClassName"))
                        .withValue("dataSourceProperties.dataSource.user", dockerConfig.getValue("dockerConfig.dbUser"))
                        .withValue("dataSourceProperties.dataSource.password", dockerConfig.getValue("dockerConfig.dbPassword"))
                        .withValue("database", ConfigValueFactory.fromMap(dockerConfig.getObject("database")))

                it.installDefaultDatabaseConfig(dbConfig)

                val dbDockerfile = dockerConfig.getString("dockerConfig.dbDockerfile")
                // Override the port and hostname parameters in dockerfile
                val dbDockerfileArgs = defaultUrlArgs.withFallback(
                        dockerConfig.getConfig("dockerConfig.dbDockerfileArgs")).resolve()
                val database = mutableMapOf(
                        "build" to mapOf(
                             "context" to project.buildDir.absolutePath,
                             "dockerfile" to project.buildDir.resolve(dbDockerfile).toString(),
                             "args" to dbDockerfileArgs.entrySet().associate { it.key to it.value.unwrapped() }
                        ),
                        "restart" to "unless-stopped"
                )

                // append persistence volume if it is required
                if (dockerConfig.hasPath("dockerConfig.dbDataVolume")) {

                    var hostPathStr = dockerConfig.getString("dockerConfig.dbDataVolume.hostPath")

                    if (dockerConfig.hasPath("dockerConfig.dbDataVolume.hostPathArgs")) {
                        val hostPathArgs = dockerConfig.getConfig("dockerConfig.dbDataVolume.hostPathArgs")
                        hostPathStr = TypesafeUtils.resolveString(hostPathStr, hostPathArgs)
                    }

                    var containerPathStr = dockerConfig.getString("dockerConfig.dbDataVolume.containerPath")

                    if (dockerConfig.hasPath("dockerConfig.dbDataVolume.containerPathArgs")) {
                        val containerPathArgs = dockerConfig.getConfig("dockerConfig.dbDataVolume.containerPathArgs")
                        containerPathStr = TypesafeUtils.resolveString(containerPathStr, containerPathArgs)
                    }

                    val hostPath = Paths.get(hostPathStr)
                    val absoluteHostPath = when {
                        hostPath.isAbsolute -> hostPath
                        else -> nodeBuildPath.resolve(hostPath).toAbsolutePath()
                    }
                    val hostDir = absoluteHostPath.toFile()
                    if (hostDir.mkdirs() || hostDir.isDirectory) {
                        volumes["$dbHost-volume"] = mapOf(
                                "driver" to "local",
                                "driver_opts" to mapOf(
                                        "type" to "none",
                                        "device" to absoluteHostPath.toString(),
                                        "o" to "bind"
                                )
                        )
                        database["volumes"] = listOf("$dbHost-volume:$containerPathStr")
                    } else {
                        throw InvalidUserDataException("The external path provided could not be created")
                    }
                }
                // add database service
                services[dbHost] = database

                // attach database dependency to the node service
                service["depends_on"] = listOf(dbHost)
            }

            services[it.containerName] = service
        }

        if (external.containerImage.isPresent()) {

            val extra = mutableMapOf(
                    "container_name" to external.containerName.get(),
                    "image" to external.containerImage.get(),
                    "ports" to external.servicePorts.get().map {QuotedString("${it}:${it}") }
            )
            if(external.volumes.get().isNotEmpty()){
                extra["volumes"] = external.volumes.get().map{ "${it["sourceFile"]}:${it["deploymentPath"]}" }
            }
            if(external.environment.get().isNotEmpty()){
                extra["environment"] = external.environment.get().map { "${it.key}=${it.value}" }
            }
            if(external.privileged.get() == true) {
                extra["privileged"] = external.privileged.get()
            }
            if(external.commands.isPresent()){
                extra["command"] = external.commands.get()
            }
            services["${external.containerName.get()}"] = extra
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

    private class QuotedString(val value: String)

    private class DockerComposeRepresenter : Representer() {

        private inner class RepresentQuotedString : Represent {
            override fun representData(data: Any): org.yaml.snakeyaml.nodes.Node? {
                val str = data as QuotedString
                return representScalar(Tag.STR, str.value, DumperOptions.ScalarStyle.DOUBLE_QUOTED.char)
            }
        }

        init {
            this.representers[QuotedString::class.java] = RepresentQuotedString()
        }
    }
}