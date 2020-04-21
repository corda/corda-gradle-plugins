package net.corda.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

open class DockerImage : DefaultTask() {

    private companion object{
        private const val DOCKER_FILE_NAME = "dockerfile"
        private const val CORDA_VERSION_PROP = "corda_release_version"
    }

    private enum class Images(val label: String) {
        ZULU("corda/corda-zulu-java1.8"),
        CORRETTO("corda/corda-corretto-java1.8"),
        ENTERPRISE("corda/corda-enterprise-node-alpine-zulu-java1.8");

        fun getAddress(cordaVersion:String): String{
            return "${label}-${cordaVersion.toLowerCase()}:latest"
        }
    }

    init {
        description = "Creates a docker file and immediately builds that image to the local repository."
    }

    @get:Optional
    @get:Input
    internal var baseImage: String = Images.ZULU.name

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

        project.logger.lifecycle("Running DockerImage task")
        project.copy {
            it.apply {
                from("${project.buildDir}/libs", "*.jar")
                into("${project.buildDir}/docker")
            }
        }

        val imageAddress = getImageTag()
        project.logger.lifecycle("Using Image: ${imageAddress}")
        val dockerFileContents = StringBuilder()

        dockerFileContents
            .appendln(" FROM $imageAddress")
            .appendln("COPY build/docker/*.jar /opt/corda/cordapps/")

        if(trustRootStoreName != null){
            project.logger.lifecycle("Copying Trust Store: ${trustRootStoreName}")
            project.logger.lifecycle("Copying From: ${project.projectDir}")

            project.copy {
                it.apply {
                    from("${project.projectDir}/${trustRootStoreName}")
                    into("${project.buildDir}/docker/")
                }
            }
            dockerFileContents.appendln("COPY build/docker/${trustRootStoreName} /opt/corda/certificates/")
        }

        File("${project.buildDir}/docker/$DOCKER_FILE_NAME").writeText(dockerFileContents.toString())
        project.exec{
            it.commandLine("docker", "build", ".", "-t", "${project.name}:${project.version}","-f", "${project.buildDir}/docker/$DOCKER_FILE_NAME" )
        }
    }

    private fun getImageTag(): String{
        val cordaVersion = project.properties[CORDA_VERSION_PROP] as String
        if(baseImage == Images.CORRETTO.name) {
            return Images.CORRETTO.getAddress(cordaVersion)
        }else if(baseImage == Images.ZULU.name){
            return Images.ZULU.getAddress(cordaVersion)
        }else if(baseImage == Images.ENTERPRISE.name){
            return Images.ENTERPRISE.getAddress(cordaVersion)
        }else if (baseImage.isNotEmpty()){
            project.logger.lifecycle("Using custom image $baseImage")
            return baseImage
        }
        return Images.ZULU.getAddress(cordaVersion)
    }

}