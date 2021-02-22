package net.corda.plugins.cpk

import aQute.bnd.build.model.EE
import aQute.bnd.osgi.Analyzer
import aQute.bnd.osgi.Constants.OPTIONAL
import aQute.bnd.osgi.Constants.RESOLUTION_DIRECTIVE
import aQute.bnd.osgi.Constants.STRICT
import aQute.bnd.osgi.Descriptors.TypeRef
import aQute.bnd.osgi.Jar
import aQute.bnd.osgi.Verifier
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File
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

    // Enable strict verification by default when we upgrade to Bnd 5.3.
    @get:Input
    val strict: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [VerifyBundle] by accident.
     */
    internal fun setDependenciesFrom(task: TaskProvider<DependencyCalculator>) {
        _classpath.setFrom(task.flatMap(DependencyCalculator::externalJars), task.flatMap(DependencyCalculator::libraries))
        _classpath.disallowChanges()
        dependsOn(task)
    }

    @TaskAction
    fun verify() {
        Jar(bundle.get().asFile).use(::verify)
    }

    private fun verify(jar: Jar) {
        Verifier(jar).use { verifier ->
            verifier.setProperty(STRICT, strict.get().toString())
            verifier.verify()
            verifyImportPackage(verifier)

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

    private fun verifyImportPackage(verifier: Verifier) {
        val analyzer = verifier.parent as Analyzer
        analyzer.analyze()

        val packageSpace = analyzer.classspace.keys.mapTo(HashSet(), TypeRef::getPackageRef)
        val classpathPackages = getClasspathPackages()
        val systemPackages = analyzer.highestEE?.let { EE.parse(it.ee) }?.packages ?: emptyMap<String, Any>()

        analyzer.imports.forEach { importPackage ->
            val packageRef = importPackage.key
            val packageName = packageRef.fqn
            if (!packageSpace.contains(packageRef)
                    && !systemPackages.containsKey(packageName)
                    && !classpathPackages.contains(packageName)
                    && (importPackage.value[RESOLUTION_DIRECTIVE] != OPTIONAL)) {
                verifier.error("Import Package clause found for missing package [%s]", packageName)
            }
        }
    }

    private fun getClasspathPackages(): Set<String> {
        return classpath.files.flatMapTo(HashSet(), File::packages)
    }
}
