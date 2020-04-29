package net.corda.plugins

import org.gradle.api.DefaultTask
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.LoggingBuildHandler
import org.gradle.api.file.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject


open class DockerImage  @Inject constructor(objects: ObjectFactory, layouts: ProjectLayout) : DefaultTask() {

    private companion object{
        private const val DOCKER_FILE_NAME = "Dockerfile"
    }

    init {
        description = "Creates a docker file and immediately builds that image to the local repository."
        group = "CordaDockerDeployment"
    }

    @get:Input
    internal var baseImage: String? = null

    fun baseImage(baseImage: String) {
        this.baseImage = baseImage
    }

    @get:Optional
    @get:InputFile
    internal var trustRootStoreFile: File? = null

    fun trustRootStoreFile(trustRootStoreFile: File) {
        this.trustRootStoreFile = trustRootStoreFile
    }

    @get:Optional
    @get:Input
    internal var buildImage: Boolean = true

    fun buildImage(doBuild: Boolean) {
        this.buildImage = doBuild
    }

    @get:Optional
    @get:Input
    internal var dockerImageTag: String? = null

    fun dockerImageTag(tag: String) {
        dockerImageTag = tag
    }

    private val _jars: ConfigurableFileCollection = project.files(project.configuration("cordapp"))

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val cordaJars: FileCollection get() = _jars

    @Suppress("MemberVisibilityCanBePrivate")
    fun setCordaJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.from(files)
    }
    fun cordaJars(inputs: Any?) = setCordaJars(inputs)

    @get:Internal
    val outputDir: DirectoryProperty = objects.directoryProperty().convention(layouts.buildDirectory.dir("docker"))

    @TaskAction
    fun build() {

        copyJarsToBuildDir()
        writeDockerFile()

        if(buildImage){
            val docker = DefaultDockerClient.fromEnv().build()
            val builtImageId = buildDockerFile(docker)
            val tag = dockerImageTag?:"${project.name}:${project.version}"

            logger.lifecycle("Tagging $builtImageId as: $tag")
            docker.tag(builtImageId, tag)
        }
    }

    private fun writeDockerFile() {
        logger.lifecycle("Using Image: ${baseImage}")
        val copyTrustRootStore: String = getAdditionalTrustRootCommandIfNeeded()

        val dockerFileContents = """\
            |FROM $baseImage
            |COPY *.jar /opt/corda/cordapps/
            |$copyTrustRootStore""".trimMargin()

        logger.lifecycle("Writing $DOCKER_FILE_NAME to ${outputDir.get()}")
        project.file(outputDir.file(DOCKER_FILE_NAME)).writeText(dockerFileContents)
    }

    private fun copyJarsToBuildDir() {
        project.copy {
            it.apply {
                from(cordaJars)
                into(outputDir)
            }
        }
    }

    private fun buildDockerFile(docker:DefaultDockerClient):  String{
        val path: Path = Paths.get(outputDir.get().toString())
        return docker.build(path,null, DOCKER_FILE_NAME, LoggingBuildHandler())
    }

    private fun getAdditionalTrustRootCommandIfNeeded(): String {
        if (trustRootStoreFile != null) {
            logger.lifecycle("Copying Trust Store: ${trustRootStoreFile}")
            logger.lifecycle("Copying From: ${project.projectDir}")

            project.copy {
                it.apply {
                    from(trustRootStoreFile)
                    into(outputDir)
                }
            }
            return "COPY ${trustRootStoreFile!!.name} /opt/corda/tmp-certificates/"
        }
        return ""
    }
}