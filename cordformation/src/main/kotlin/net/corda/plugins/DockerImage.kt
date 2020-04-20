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

    private enum class Images(val label: String,val enterpriseLabel: String) {
        ZULU("zulu","alpine-zulu"),
        CORRETTO("corretto", "" );

        fun getAddress(cordaVersion:String, isEnterprise:Boolean): String{
            if(isEnterprise){
                return "entdocker/corda-enterprise-${enterpriseLabel}-java1.8-${cordaVersion.toLowerCase()}:latest"
            }
            return "corda/corda-${label}-java1.8-${cordaVersion.toLowerCase()}:latest"
        }
    }


    init {
        description = "Creates a docker file and immediately builds that image to the local repository."
    }

    @get:Optional
    @get:Input
    internal var baseImage: String = Images.ZULU.label

    fun baseImage(baseImage: String) {
        this.baseImage = baseImage
    }

    @get:Optional
    @get:Input
    internal var enterprise: Boolean = false

    fun enterpriseEdition(editionInput: Boolean) {
        this.enterprise = editionInput
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
        File("${project.buildDir}/docker/$DOCKER_FILE_NAME").writeText("""
            FROM $imageAddress
            COPY build/docker/*.jar /opt/corda/cordapps/
        """.trimIndent())

        project.exec{
            it.commandLine("docker", "build", ".", "-t", "${project.name}:${project.version}","-f", "${project.buildDir}/docker/$DOCKER_FILE_NAME" )
        }
    }


    fun getImageTag(): String{
        val cordaVersion = project.properties[CORDA_VERSION_PROP] as String
        if(baseImage.equals(Images.CORRETTO.label)) {
            return Images.CORRETTO.getAddress(cordaVersion,enterprise)
        }else if(baseImage.equals(Images.ZULU.label)){
            return Images.ZULU.getAddress(cordaVersion,enterprise)
        }else if (baseImage.isNotEmpty()){
            project.logger.lifecycle("Using custom image $baseImage")
            return baseImage
        }
        return Images.ZULU.getAddress(cordaVersion,enterprise)
    }

}