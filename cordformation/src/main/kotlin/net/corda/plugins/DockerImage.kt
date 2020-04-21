package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import com.spotify.docker.client.DefaultDockerClient
import org.apache.commons.lang.StringUtils
import java.nio.file.Path
import java.nio.file.Paths


open class DockerImage : DefaultTask() {

    private companion object{
        //Currently a bug in gradle's projectFile that writes the file with a lower case 'd'
        //using this name ensures that the docker plugin can find the file as expected.
        private const val DOCKER_FILE_NAME = "dockerfile"
    }

    init {
        description = "Creates a docker file and immediately builds that image to the local repository."
        group = "CordaDockerDeployment"
    }

    @get:Optional
    @get:Input
    internal var baseImage: String? = null

    fun baseImage(baseImage: String) {
        this.baseImage = baseImage
    }

    @get:Optional
    @get:Input
    internal var trustRootStoreName: String? = null

    fun trustRootStoreName(trustRoot: String) {
        this.trustRootStoreName = trustRoot
    }


    @TaskAction
    fun build() {

        logger.lifecycle("Running DockerImage task")
        project.copy {
            it.apply {
                from("${project.buildDir}/libs", "*.jar")
                into("${project.buildDir}/docker")
            }
        }

        logger.lifecycle("Using Image: ${baseImage}")
        val copyTrustRootStore:String = getAdditionalTrustRootCommandIfNeeded()

        val dockerFileContents= """
            FROM $baseImage
            COPY *.jar /opt/corda/cordapps/
            $copyTrustRootStore""".trimIndent()

        val dockerFilePath = "${project.buildDir}/docker/$DOCKER_FILE_NAME"

        logger.lifecycle("Writing Dockerfile to $dockerFilePath")
        project.file(dockerFilePath).writeText(dockerFileContents)
        val docker = DefaultDockerClient.fromEnv().build()

        val builtImageId = buildDockerFile(docker)

        val tag = "${project.name}:${project.version}"

        logger.lifecycle("Tagging $builtImageId as: $tag")
        docker.tag(builtImageId, tag)

    }

    private fun buildDockerFile(docker:DefaultDockerClient):  String{
        val path: Path = Paths.get(project.buildDir.absolutePath, "docker")

        return docker.build(path, DOCKER_FILE_NAME)
    }

    private fun getAdditionalTrustRootCommandIfNeeded(): String {
        if (trustRootStoreName != null) {
            logger.lifecycle("Copying Trust Store: ${trustRootStoreName}")
            logger.lifecycle("Copying From: ${project.projectDir}")

            project.copy {
                it.apply {
                    from("${project.projectDir}/${trustRootStoreName}")
                    into("${project.buildDir}/docker/")
                }
            }
            return "COPY ${trustRootStoreName} /opt/corda/certificates/"
        }
        return StringUtils.EMPTY
    }


}