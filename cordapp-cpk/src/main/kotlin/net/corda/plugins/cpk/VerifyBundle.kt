package net.corda.plugins.cpk

import aQute.bnd.osgi.Constants.STRICT
import aQute.bnd.osgi.Jar
import aQute.bnd.osgi.Verifier
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class VerifyBundle @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        description = "Verifies that a bundle's OSGi meta-data is consistent."
        group = GROUP_NAME
    }

    @get:PathSensitive(RELATIVE)
    @get:InputFile
    val bundle: RegularFileProperty = objects.fileProperty()

    private val _classpath: ConfigurableFileCollection = objects.fileCollection()
    val classpath: FileCollection
        @Classpath
        @InputFiles
        get() = _classpath

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [VerifyBundle] by accident.
     */
    internal fun setDependenciesFrom(task: TaskProvider<DependencyCalculator>) {
        _classpath.setFrom(task.flatMap(DependencyCalculator::externalJars), task.flatMap(DependencyCalculator::dependencies))
        _classpath.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun verify() {
        Jar(bundle.get().asFile).use(::verify)
    }

    private fun verify(jar: Jar) {
        Verifier(jar).use { verifier ->
            verifier.setProperty(STRICT, "true")
            verifier.verify()

            val jarName = jar.source.name
            for (warning in verifier.warnings) {
                logger.warn("{}: {}", jarName, warning)
            }

            if (verifier.errors.isNotEmpty()) {
                for (error in verifier.errors) {
                    logger.error("{}: {}", jarName, error)
                }
                throw InvalidUserDataException("Bundle $jarName has validation errors:"
                        + verifier.errors.joinToString(System.lineSeparator(), System.lineSeparator()))
            }
        }
    }
}
