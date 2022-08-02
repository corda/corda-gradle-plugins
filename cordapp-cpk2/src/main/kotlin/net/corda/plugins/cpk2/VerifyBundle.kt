package net.corda.plugins.cpk2

import aQute.bnd.build.model.EE
import aQute.bnd.header.Attrs
import aQute.bnd.header.OSGiHeader
import aQute.bnd.osgi.Analyzer
import aQute.bnd.osgi.Constants.EXPORT_PACKAGE
import aQute.bnd.osgi.Constants.OPTIONAL
import aQute.bnd.osgi.Constants.RESOLUTION_DIRECTIVE
import aQute.bnd.osgi.Constants.STRICT
import aQute.bnd.osgi.Constants.VERSION_ATTRIBUTE
import aQute.bnd.osgi.Descriptors.PackageRef
import aQute.bnd.osgi.Descriptors.TypeRef
import aQute.bnd.osgi.Jar
import aQute.bnd.osgi.Verifier
import aQute.bnd.version.Version
import aQute.bnd.version.VersionRange
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.function.Function
import javax.inject.Inject

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
@DisableCachingByDefault
open class VerifyBundle @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    init {
        description = "Verifies that a bundle's OSGi meta-data is consistent."
        group = CORDAPP_TASK_GROUP
    }

    @get:PathSensitive(RELATIVE)
    @get:InputFile
    val bundle: RegularFileProperty = objects.fileProperty()

    private val _classpath: ConfigurableFileCollection = objects.fileCollection()
    val classpath: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _classpath

    @get:Input
    val strict: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * Don't eagerly configure the [DependencyCalculator] task, even if
     * someone eagerly configures this [VerifyBundle] by accident.
     */
    internal fun setDependenciesFrom(task: TaskProvider<DependencyCalculator>) {
        _classpath.setFrom(
            /**
             * These jars do not belong to this CPK, but provide
             * packages that the CPK will need at runtime.
             */
            task.flatMap(DependencyCalculator::providedJars),
            task.flatMap(DependencyCalculator::remoteCordapps),
            task.flatMap(DependencyCalculator::projectCordapps),

            /**
             * These jars are the contents of this CPK's lib folder.
             */
            task.flatMap(DependencyCalculator::libraries)
        )
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
            verifyReservedPackage(verifier)

            val jarName = jar.source.name
            for (warning in verifier.warnings) {
                logger.warn("{}: {}", jarName, warning)
            }

            if (verifier.errors.isNotEmpty()) {
                for (error in verifier.errors) {
                    logger.error("{}: {}", jarName, error)
                }
                logger.error(
                    "Ensure that dependencies are OSGi bundles, and that they export every package {} needs to import.",
                        jarName)
                throw InvalidUserDataException("Bundle $jarName has validation errors:"
                    + verifier.errors.joinToString(System.lineSeparator(), System.lineSeparator()))
            }
        }
    }

    private fun verifyReservedPackage(verifier: Verifier) {
        val reservedPackageName = Regex("^net.corda(\\..+)?$")
        verifier.exportPackage
            .keyList()
            .asSequence()
            .plus(verifier.privatePackage.keyList())
            .filter(reservedPackageName::matches)
            .forEach { packageName -> verifier.error("Export Package clause found for Corda package [%s]", packageName) }
    }

    private fun verifyImportPackage(verifier: Verifier) {
        val analyzer = verifier.parent as Analyzer
        analyzer.analyze()

        val packageSpace = analyzer.classspace.keys.mapTo(HashSet(), TypeRef::getPackageRef)
        val systemPackages = analyzer.highestEE?.let { EE.parse(it.ee) }?.packages ?: emptyMap<String, Any>()
        val classpathPackages = getClasspathPackages()
        val exportVersions = mutableMapOf<String, MutableSet<Version>>()
        fetchClasspathVersions(exportVersions)

        analyzer.exports.forEach { exportPackage ->
            exportPackage.mapVersionsTo(exportVersions, Function(PackageRef::getFQN))
        }
        analyzer.imports
            .filterKeys { packageRef -> !systemPackages.contains(packageRef.fqn) }
            .filterValues { value -> value[RESOLUTION_DIRECTIVE] != OPTIONAL }
            .forEach { importPackage ->
                val packageRef = importPackage.key
                val packageName = packageRef.fqn
                if (!packageSpace.contains(packageRef)
                        && !classpathPackages.contains(packageName)) {
                    verifier.error("Import Package clause found for missing package [%s]", packageName)
                }

                val importVersion = importPackage.value[VERSION_ATTRIBUTE]
                val exportVersion = exportVersions[packageName]
                if (importVersion != null
                        && (exportVersion == null || exportVersion.none(VersionRange(importVersion)::includes))) {
                    verifier.error("Import Package clause requires package [%s] with version '%s', but version(s) '%s' exported",
                        packageName, importVersion, exportVersion?.joinToString())
                }
            }
    }

    private fun getClasspathPackages(): Set<String> {
        return classpath.flatMapTo(HashSet(), File::packages)
    }

    private fun fetchClasspathVersions(exportVersions: MutableMap<String, MutableSet<Version>>) {
        classpath.forEach { file ->
            val exportPackage = file.manifest.mainAttributes.getValue(EXPORT_PACKAGE)
            if (exportPackage != null) {
                OSGiHeader.parseHeader(exportPackage).forEach { header ->
                    header.mapVersionsTo(exportVersions, Function.identity())
                }
            }
        }
    }

    private fun <T> Map.Entry<T, Attrs>.mapVersionsTo(
        target: MutableMap<String, MutableSet<Version>>,
        keyMapping: Function<T, String>
    ) {
        target.compute(keyMapping.apply(key)) { _, packageVersions ->
            (packageVersions ?: mutableSetOf()).also { versions ->
                val packageVersion = value[VERSION_ATTRIBUTE]
                if (packageVersion != null) {
                    versions.add(Version(packageVersion))
                }
            }
        }
    }
}
