package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
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
        private val defaultDirectory: Path = Paths.get("build", "docker")

        private const val dockerComposeFileVersion = "3"

        private val yamlOptions = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }
        private val yaml = Yaml(yamlOptions)
    }

    private val directoryPath: Path = project.projectDir.toPath().resolve(directory)


    val dockerComposePath: Path
        @get:InputFile
        get() {
            val wantedPath = directoryPath.resolve("docker-compose.yml")
            if (!Files.exists(wantedPath)) {
                Files.createFile(wantedPath)
            }
            return wantedPath
        }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running Cordform task")
        initializeConfiguration()
        nodes.forEach { it -> it.installDockerConfig(22022) }
        installCordaJar()
        nodes.forEach(Node::installDrivers)
        generateKeystoreAndSignCordappJar()
        generateExcludedWhitelist()
        bootstrapNetwork()
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
                    "ports" to listOf(it.rpcPort, it.config.getInt("sshd.port")),
                    "image" to "corda/corda-zulu-${it.runtimeVersion().toLowerCase()}"
            )
        }.toMap()


        val dockerComposeObject = mapOf(
                "version" to dockerComposeFileVersion,
                "services" to services)

        val dockerComposeContent = yaml.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray(StandardCharsets.UTF_8))
    }
}

