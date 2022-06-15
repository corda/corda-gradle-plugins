package net.corda.plugins.cpk

import java.io.File
import java.util.jar.Attributes
import java.util.jar.Attributes.Name
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.osgi.framework.Constants.BUNDLE_MANIFESTVERSION
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class VerifyLibraries @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        description = "Verifies that a CPK's libraries are all bundles."
        group = CORDAPP_TASK_GROUP
    }

    private val _libraries: ConfigurableFileCollection = objects.fileCollection()
    val libraries: FileCollection
        @PathSensitive(RELATIVE)
        @SkipWhenEmpty
        @InputFiles
        get() = _libraries

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [VerifyLibraries] by accident.
     */
    internal fun setDependenciesFrom(task: TaskProvider<DependencyCalculator>) {
        _libraries.setFrom(
            /**
             * These jars are the contents of this CPK's lib/ folder.
             */
            task.flatMap(DependencyCalculator::libraries)
        )
        _libraries.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun verify() {
        for (library in libraries.files) {
            with(library.manifest.mainAttributes) {
                requireAttribute(BUNDLE_MANIFESTVERSION, library)
                requireAttribute(BUNDLE_SYMBOLICNAME, library)
                requireAttribute(BUNDLE_VERSION, library)
            }
        }
    }

    private fun Attributes.requireAttribute(attrName: String, library: File) {
        if (!containsKey(Name(attrName))) {
            logger.error("Library {} is not an OSGi bundle. Try declaring it as a 'cordaEmbedded' dependency instead.",
                library.name)
            throw InvalidUserDataException("Library ${library.name} has no $attrName attribute")
        }
    }
}
