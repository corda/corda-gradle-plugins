package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections.unmodifiableSet
import java.util.StringJoiner
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DependencyConstraintsTask @Inject constructor(
    objects: ObjectFactory,
    providers: ProviderFactory
) : DefaultTask() {
    private companion object {
        const val DEPENDENCY_CONSTRAINTS = "META-INF/DependencyConstraints"
        const val CORDAPP_HASH_ALGORITHM = "SHA-256"
        const val NON_CORDA = false
        const val CORDA = true
        const val DELIMITER = ','
        const val CRLF = "\r\n"

        private val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
            "org.jetbrains.kotlin" to "*",
            "org.slf4j" to "slf4j-api",
            "org.slf4j" to "jcl-over-slf4j",
            "commons-logging" to "commons-logging",
            "co.paralleluniverse" to "quasar-core"
        ))
    }

    init {
        description = "Computes the constraints for this CorDapp's dependencies."
        group = GROUP_NAME
    }

    private val _dependencies: ConfigurableFileCollection = objects.fileCollection().from(
        providers.provider(::getNonCordaDependencies)
    ).apply {
        disallowChanges()
    }

    val dependencies: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() {
            _dependencies.finalizeValue()
            return _dependencies
        }

    @get:Input
    val algorithm: Property<String> = objects.property(String::class.java).convention(CORDAPP_HASH_ALGORITHM)

    @get:Internal
    val constraintsDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputFile
    val constraintsOutput: Provider<RegularFile> = constraintsDir.file(DEPENDENCY_CONSTRAINTS)

    @TaskAction
    fun generate() {
        val algorithmName = algorithm.get().toUpperCase()
        val digest = try {
            MessageDigest.getInstance(algorithmName)
        } catch (_ : NoSuchAlgorithmException) {
            throw InvalidUserDataException("Hash algorithm $algorithmName not available")
        }

        try {
            constraintsOutput.get().asFile.bufferedWriter().use { output ->
                dependencies.forEach { cordapp ->
                    logger.info("CorDapp dependency: {}", cordapp.name)
                    output.append(cordapp.name.replace(DELIMITER, '_')).append(DELIMITER)
                        .append(algorithmName).append(DELIMITER)
                        .append(digest.hashFor(cordapp).toHexString())
                        .append(CRLF)
                }
            }
        } catch (e: IOException) {
            throw InvalidUserCodeException(e.message ?: "", e)
        }
    }

    /**
     * Sets this task's dependencies; to be invoked when the task is configured.
     * (Deliberately NOT invoking this from the constructor because I'm unclear
     * on where [org.gradle.api.Task] construction fits into the Gradle lifecycle.
     */
    fun dependsOnConstraints() {
        dependsOn(calculateTaskDependencies())
    }

    private fun calculateTaskDependencies(): Set<TaskDependency> {
        val runtimeClasspath = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        return (runtimeClasspath.allDependencies.filterIsInstance<ProjectDependency>()
                - calculateExcludedDependencies().filterIsInstance<ProjectDependency>())
            .mapTo(LinkedHashSet(), ProjectDependency::getBuildDependencies)
    }

    private fun calculateExcludedDependencies(): Set<Dependency> {
        return with(project.configurations) {
            getByName(CORDA_IMPLEMENTATION_CONFIGURATION_NAME).allDependencies +
                getByName(CORDA_RUNTIME_CONFIGURATION_NAME).allDependencies
        }
    }

    private fun getNonCordaDependencies(): Set<File> {
        if (!project.state.executed) {
            /**
             * We need Gradle to invoke this function after project evaluation is complete,
             * as otherwise this project may not have learnt its full set of dependencies.
             */
            throw InvalidUserCodeException("Task $name configured before project has been fully evaluated.")
        }

        val configurations = project.configurations

        // Compute the (unresolved) dependencies on the runtime classpath
        // that the user has selected for this CPK archive.
        val runtimeConfiguration = configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        val excludeRuntimeDeps = configurations.getByName(CORDA_RUNTIME_CONFIGURATION_NAME).allDependencies
        val runtimeDeps = runtimeConfiguration.allDependencies - excludeRuntimeDeps

        // There are some dependencies that Corda MUST always provide.
        // Extract any of these from the user's runtime dependencies.
        val (cordaDeps, nonCordaDeps) = runtimeDeps.groupBy { dep ->
            isCordaProvided(dep.group, dep.name)
        }.let { group ->
            Pair(group[CORDA] ?: emptySet<Dependency>(), group[NON_CORDA] ?: emptySet<Dependency>())
        }

        // Compute the set of resolved artifacts that will define this CorDapp.
        val packagingConfiguration = configurations.getByName(CORDAPP_PACKAGING_CONFIGURATION_NAME).resolvedConfiguration
        val packageArtifacts = nonCordaDeps.resolveFor(packagingConfiguration)
                .filterNot { artifact -> isCordaProvided(artifact.moduleVersion.id) } -
            excludeRuntimeDeps.resolveFor(packagingConfiguration) -
            cordaDeps.resolveFor(packagingConfiguration)

        // Corda artifacts should not be included, either directly or transitively.
        // However, we will make an exception for any which have been declared as CorDapps.
        forbidCordaArtifacts(packageArtifacts - cordappArtifactsFor(packagingConfiguration))

        return packageArtifacts.mapTo(LinkedHashSet(), ResolvedArtifact::getFile)
    }

    private fun Collection<Dependency>.resolveFor(configuration: ResolvedConfiguration): Set<ResolvedArtifact> {
        return configuration.getFirstLevelModuleDependencies { contains(it) }
            .flatMapTo(LinkedHashSet(), ResolvedDependency::getAllModuleArtifacts)
    }

    private fun cordappArtifactsFor(configuration: ResolvedConfiguration): Set<ResolvedArtifact> {
        val cordapps = project.configurations.getByName(CORDAPP_CONFIGURATION_NAME).allDependencies
        return configuration.getFirstLevelModuleDependencies { cordapps.contains(it) }
            .flatMapTo(LinkedHashSet(), ResolvedDependency::getModuleArtifacts)
    }

    private fun forbidCordaArtifacts(artifacts: Iterable<ResolvedArtifact>) {
        for (artifact in artifacts) {
            if (isCordaArtifact(artifact)) {
                throw InvalidUserDataException("CorDapp must not contain '${artifact.moduleVersion}' (${artifact.file.name})")
            }
        }
    }

    private fun isCordaArtifact(artifact: ResolvedArtifact): Boolean {
        val group = artifact.moduleVersion.id.group
        return group.belongsTo("net.corda") || group.belongsTo("com.r3.corda")
    }

    private fun String.belongsTo(parent: String): Boolean {
        val parentLength = parent.length
        return startsWith(parent) && (length == parentLength || (length > parentLength && this[parentLength] == '.'))
    }

    private fun isCordaProvided(id: ModuleVersionIdentifier): Boolean {
        return isCordaProvided(id.group, id.name)
    }

    private fun isCordaProvided(group: String?, name: String?): Boolean {
        return HARDCODED_EXCLUDES.any {
            exclude -> (exclude.first == group) && (exclude.second == "*" || exclude.second == name)
        }
    }

    /**
     * For computing file hashes.
     */
    private fun MessageDigest.hashFor(file: File): ByteArray = hashFor(file.inputStream())

    private fun MessageDigest.hashFor(input: InputStream): ByteArray {
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length = it.read(buffer)
                if (length == -1) {
                    break
                }
                update(buffer, 0, length)
            }
        }
        return digest()
    }

    private fun ByteArray.toHexString(): String {
        return with(StringJoiner("")) {
            for (b in this@toHexString) {
                add(String.format("%02x", b))
            }
            toString()
        }
    }
}
