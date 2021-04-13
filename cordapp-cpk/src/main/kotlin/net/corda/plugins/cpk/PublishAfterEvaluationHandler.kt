package net.corda.plugins.cpk

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.TaskContainer
import org.w3c.dom.Element
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.Function

class PublishAfterEvaluationHandler(rootProject: Project) : Action<Gradle> {
    private val logger: Logger = rootProject.logger
    private var artifactoryPublisher: ArtifactoryPublisher? = null

    private fun enableArtifactoryPublisher(plugin: Plugin<*>) {
        artifactoryPublisher = try {
            ArtifactoryPublisher(plugin, logger)
        } catch (_: Exception) {
            logger.warn("Cannot publish CPK companion POM to Artifactory")
            null
        }
    }

    init {
        rootProject.plugins.withId("com.jfrog.artifactory") { plugin ->
            enableArtifactoryPublisher(plugin)
        }
    }

    override fun execute(gradle: Gradle) {
        for (project in gradle.rootProject.allprojects) {
            // The plugin ID is the only reliable way to check
            // whether a particular plugin has been applied.
            // Each sub-project can load its plugins into its
            // own classloader, which makes all of the [Plugin]
            // implementation classes different.
            if (project.plugins.hasPlugin(CORDAPP_CPK_PLUGIN_ID)) {
                publishCompanionFor(project)
            }
        }
    }

    private fun publishCompanionFor(project: Project) {
        val publications = (project.extensions.findByType(PublishingExtension::class.java) ?: return).publications
        val pomXmlWriter = PomXmlWriter(project.configurations)
        publications.withType(MavenPublication::class.java)
            .matching { it.pom.packaging == "jar" && !it.groupId.isNullOrEmpty() }
            .all { pub ->
                // Create a "companion" POM to support transitive CPK relationships.
                val publicationProvider = publications.register("cpk-${pub.name}-companion", MavenPublication::class.java) { cpk ->
                    cpk.groupId = toCompanionGroupId(pub.groupId, pub.artifactId)
                    cpk.artifactId = toCompanionArtifactId(pub.artifactId)
                    cpk.version = pub.version
                    cpk.pom { pom ->
                        pom.packaging = "pom"
                        pom.url.set(pub.pom.url)
                        pom.description.set(pub.pom.description)
                        pom.inceptionYear.set(pub.pom.inceptionYear)
                        pom.withXml(pomXmlWriter)
                    }
                }
                artifactoryPublisher?.run {
                    publish(project.tasks, pub, publicationProvider)
                }
            }
    }
}

private class PomXmlWriter(private val configurations: ConfigurationContainer) : Action<XmlProvider> {
    override fun execute(xml: XmlProvider) {
        val compileClasspath = configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration
        val dependencies = xml.asElement().appendElement("dependencies")

        val cordappWriter = DependencyXmlWriter(dependencies, Function(::CordappCoordinate))
        val cordappDependencies = configurations.getByName(CORDAPP_CONFIGURATION_NAME).allDependencies
        compileClasspath.resolveFirstLevel(cordappDependencies).forEach(cordappWriter::write)

        val providedWriter = DependencyXmlWriter(dependencies, Function(::ProvidedCoordinate))
        val providedDependencies = configurations.getByName(CORDA_PROVIDED_CONFIGURATION_NAME).allDependencies
        compileClasspath.resolveFirstLevel(providedDependencies).forEach(providedWriter::write)
    }
}

private class DependencyXmlWriter(
    private val dependencies: Element,
    private val coordinateFactory: Function<ResolvedArtifact, out MavenCoordinate>
) {
    fun write(artifact: ResolvedArtifact) {
        val maven = coordinateFactory.apply(artifact)
        val dependency = dependencies.appendElement("dependency")
        dependency.appendElement("groupId", maven.groupId)
        dependency.appendElement("artifactId", maven.artifactId)
        dependency.appendElement("version", maven.version)
        maven.classifier?.also { classifier ->
            dependency.appendElement("classifier", classifier)
        }
        if (maven.type != "jar") {
            dependency.appendElement("type", maven.type)
        }
        dependency.appendElement("scope", "compile")
    }
}

private abstract class MavenCoordinate(artifact: ResolvedArtifact) {
    @JvmField
    protected val id: ModuleVersionIdentifier = artifact.moduleVersion.id

    @JvmField
    protected val artifactName: String = artifact.name

    abstract val groupId: String?
    abstract val artifactId: String

    val version: String? get() = id.version
    val classifier: String? = artifact.classifier
    val type: String? = artifact.type
}

private class CordappCoordinate(artifact: ResolvedArtifact): MavenCoordinate(artifact) {
    override val groupId: String get() = toCompanionGroupId(id.group, artifactName)
    override val artifactId: String get() = toCompanionArtifactId(artifactName)
}

private class ProvidedCoordinate(artifact: ResolvedArtifact): MavenCoordinate(artifact) {
    override val groupId: String get() = id.group
    override val artifactId: String get() = artifactName
}

/**
 * Integrate with the `com.jfrog.artifactory` Gradle plugin, which uses a
 * [ProjectEvaluationListener][org.gradle.api.ProjectEvaluationListener] to
 * configure itself. We can therefore assume here that any `ArtifactoryTask`
 * objects have already been configured.
 *
 * All we need is the set of [MavenPublication] objects that are to be
 * published to Artifactory. If our CPK's own [MavenPublication] is
 * among them then we include its companion's publication too.
 */
private class ArtifactoryPublisher(plugin: Plugin<*>, logger: Logger) {
    private companion object {
        private const val ARTIFACTORY_TASK_NAME = "org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask"
        private const val GET_PUBLICATIONS_METHOD_NAME = "getMavenPublications"
    }

    private val artifactoryTaskClass = try {
        @Suppress("unchecked_cast")
        Class.forName(ARTIFACTORY_TASK_NAME, true, plugin::class.java.classLoader) as Class<out Task>
    } catch (e: ClassNotFoundException) {
        logger.warn("Task {} from Gradle com.jfrog.artifactory plugin is not available.", ARTIFACTORY_TASK_NAME)
        throw e
    }

    private val mavenPublications: Method = try {
        artifactoryTaskClass.getMethod(GET_PUBLICATIONS_METHOD_NAME)
    } catch (e: Exception) {
        logger.warn("Cannot locate $GET_PUBLICATIONS_METHOD_NAME method for ArtifactoryTask", e)
        throw e
    }

    init {
        if (!MutableCollection::class.java.isAssignableFrom(mavenPublications.returnType)) {
            logger.warn("Method {} does not return a collection type.", mavenPublications)
            throw InvalidUserCodeException()
        }
    }

    private fun getMavenPublications(task: Task): MutableCollection<MavenPublication> {
        return try {
            @Suppress("unchecked_cast")
            mavenPublications.invoke(task) as MutableCollection<MavenPublication>
        } catch (e: InvocationTargetException) {
            throw InvalidUserCodeException("Failed to extract Maven publications from $task", e.targetException)
        }
    }

    fun publish(tasks: TaskContainer, owner: MavenPublication, provider: Provider<out MavenPublication>) {
        tasks.withType(artifactoryTaskClass).configureEach { task ->
            val publications = getMavenPublications(task)
            if (publications.contains(owner)) {
                val publication = provider.get()
                if (publications.add(publication)) {
                    // Only modify the task graph if our publication wasn't already present.
                    task.dependsOn(tasks.named(getGeneratePomTaskName(publication), GenerateMavenPom::class.java))
                }
            }
        }
    }

    private fun getGeneratePomTaskName(publication: MavenPublication): String {
        // This name is documented behaviour for Gradle's maven-publish plugin.
        return "generatePomFileFor${publication.name.capitalize()}Publication"
    }
}
