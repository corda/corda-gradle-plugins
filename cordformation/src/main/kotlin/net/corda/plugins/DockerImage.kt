package net.corda.plugins

import org.gradle.api.DefaultTask
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.LoggingBuildHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

@Suppress("UnstableApiUsage", "unused")
@DisableCachingByDefault
open class DockerImage @Inject constructor(objects: ObjectFactory, layouts: ProjectLayout) : DefaultTask() {

    private companion object{
        private const val DOCKER_FILE_NAME = "Dockerfile"
    }

    init {
        description = "Creates a docker file and immediately builds that image to the local repository."
        group = "CordaDockerDeployment"
    }

    @get:Optional
    @get:Input
    internal val baseImage: Property<String> = objects.property(String::class.java)

    fun baseImage(baseImage: String?) {
        this.baseImage.set(baseImage)
    }

    @get:Optional
    @get:InputFile
    @get:PathSensitive(RELATIVE)
    internal val trustRootStoreFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    internal val buildImage: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    fun buildImage(doBuild: Boolean?) {
        this.buildImage.set(doBuild)
    }

    @get:Input
    internal val dockerImageTag: Property<String> = objects.property(String::class.java)
        .convention("${project.name}:${project.version}")

    fun dockerImageTag(tag: String) {
        dockerImageTag.set(tag)
    }

    private val _jars = objects.fileCollection()
        .from(project.configurations.getByName(DEPLOY_CORDAPP_CONFIGURATION_NAME))

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(RELATIVE)
    val cordaJars: FileCollection get() = _jars

    @Suppress("MemberVisibilityCanBePrivate")
    fun setCordaJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.from(files)
    }

    fun cordaJars(inputs: Any?) = setCordaJars(inputs)

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty().convention(layouts.buildDirectory.dir("docker"))

    @TaskAction
    fun build() {

        copyJarsToBuildDir()
        writeDockerFile()

        if (buildImage.get()) {
            val docker = DefaultDockerClient.fromEnv().build()
            val builtImageId = buildDockerFile(docker)
            val tag = dockerImageTag.get()

            logger.lifecycle("Tagging $builtImageId as: $tag")
            docker.tag(builtImageId, tag)
        }
    }

    private fun writeDockerFile() {
        val baseImageName = baseImage.get()
        logger.lifecycle("Using Image: $baseImageName")
        val copyTrustRootStore = getAdditionalTrustRootCommandIfNeeded()

        val dockerFileContents = """\
            |FROM $baseImageName
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
        return docker.build(path, null, DOCKER_FILE_NAME, LoggingBuildHandler())
    }

    private fun getAdditionalTrustRootCommandIfNeeded(): String {
        if (!trustRootStoreFile.isPresent) {
            return ""
        }
        val trustRootStore = trustRootStoreFile.get()

        logger.lifecycle("Copying Trust Store: $trustRootStore")
        logger.lifecycle("Copying From: ${project.projectDir}")

        project.copy {
            it.apply {
                from(trustRootStore)
                into(outputDir)
            }
        }
        return "COPY ${trustRootStore.asFile.name} /opt/corda/tmp-certificates/"
    }
}
