package net.corda.plugins.cpk2

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

@Suppress("UnstableApiUsage")
@DisableCachingByDefault
open class DependencyConstraintsTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        description = "Computes the constraints for this CorDapp's library dependencies."
        group = CORDAPP_TASK_GROUP
    }

    private val _libraries: ConfigurableFileCollection = objects.fileCollection()
    val libraries: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _libraries

    @get:Input
    val hashAlgorithm: Property<String> = objects.property(String::class.java)

    @get:Internal
    val constraintsDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val constraintsOutput: Provider<RegularFile> = constraintsDir.file(DEPENDENCY_CONSTRAINTS)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [DependencyConstraintsTask] by accident.
     */
    internal fun setLibrariesFrom(task: TaskProvider<DependencyCalculator>) {
        _libraries.setFrom(task.flatMap(DependencyCalculator::libraries))
        _libraries.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun generate() {
        val digest = digestFor(hashAlgorithm.get().toUpperCase())

        try {
            val xmlDocument = createXmlDocument()
            val dependencyConstraints = xmlDocument.createRootElement(CPK_XML_NAMESPACE, "dependencyConstraints")
            val encoder = Base64.getEncoder()

            libraries.forEach { library ->
                logger.info("CorDapp library dependency: {}", library.name)
                dependencyConstraints.appendElement("dependencyConstraint").also { constraint ->
                    constraint.appendElement("fileName", library.name)
                    val jarHash = digest.hashOf(library)
                    constraint.appendElement("hash", encoder.encodeToString(jarHash))
                        .setAttribute("algorithm", digest.algorithm)
                }
            }

            // Write dependency constraints as XML document.
            constraintsOutput.get().asFile.bufferedWriter().use(xmlDocument::writeTo)
        } catch (e: Exception) {
            throw (e as? RuntimeException) ?: InvalidUserDataException(e.message ?: "", e)
        }
    }

    /**
     * For computing file hashes.
     */
    private fun MessageDigest.hashOf(file: File): ByteArray {
        return file.inputStream().use(::hashFor)
    }
}
