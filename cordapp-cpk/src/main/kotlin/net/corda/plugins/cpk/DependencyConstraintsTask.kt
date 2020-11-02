package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.io.IOException
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
        const val EOF = -1

        private val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
            "org.jetbrains.kotlin" to "*",
            "org.osgi" to "*",
            "org.slf4j" to "slf4j-api",
            "org.slf4j" to "jcl-over-slf4j",
            "commons-logging" to "commons-logging",
            "co.paralleluniverse" to "quasar-core",
            "co.paralleluniverse" to "quasar-core-osgi"
        ))
    }

    init {
        description = "Computes the constraints for this CorDapp's dependencies."
        group = GROUP_NAME
    }

    private val _embeddedJars: ConfigurableFileCollection = objects.fileCollection()
    private val _dependencies: ConfigurableFileCollection = objects.fileCollection().from(
        providers.provider(::getNonCordaDependencies)
    ).apply(HasConfigurableValue::finalizeValueOnRead)
        .apply(HasConfigurableValue::disallowUnsafeRead)
        .apply(HasConfigurableValue::disallowChanges)

    /**
     * Invoking [getNonCordaDependencies] will set both
     * the [dependencies] and [embeddedJars] properties.
     */
    val dependencies: FileCollection
        @PathSensitive(RELATIVE)
        @InputFiles
        get() = _dependencies

    val embeddedJars: Provider<Set<FileSystemLocation>>
        @OutputFiles
        get() = _embeddedJars.elements

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
        val cordapps = project.configurations.getByName(CORDAPP_CONFIGURATION_NAME)
        return (runtimeClasspath.allDependencies.filterIsInstance<ProjectDependency>()
                + cordapps.allDependencies.filterIsInstance<ProjectDependency>()
                - calculateExcludedDependencies().filterIsInstance<ProjectDependency>())
            .mapTo(LinkedHashSet(), ProjectDependency::getBuildDependencies)
    }

    private fun calculateExcludedDependencies(): Set<Dependency> {
        return with(project.configurations) {
            getByName(CORDA_PROVIDED_CONFIGURATION_NAME).allDependencies +
                getByName(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME).allDependencies
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
        // that the user has selected for this CPK archive. We ignore any
        // dependencies from the cordaRuntimeOnly configuration because
        // these will be provided by Corda.
        val runtimeConfiguration = configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        val runtimeDeps = DependencyCollector(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
            .collectFrom(runtimeConfiguration)

        // There are some dependencies that Corda MUST always provide,
        // even if the CorDapp also declares them itself.
        // Extract any of these from the user's runtime dependencies.
        val (cordaDeps, nonCordaDeps) = runtimeDeps.groupBy { dep ->
            isCordaProvided(dep.group, dep.name)
        }.let { group ->
            Pair(group[CORDA] ?: emptySet<Dependency>(), group[NON_CORDA] ?: emptySet<Dependency>())
        }

        // Compute the set of resolved artifacts that will define this CorDapp.
        val packagingConfiguration = runtimeConfiguration.resolvedConfiguration
        val packageFiles = (
            nonCordaDeps.resolveFor(packagingConfiguration)
                .filterNot { artifact -> isCordaProvided(artifact.moduleVersion.id) }
                .also { artifacts ->
                    // Corda artifacts should not be included, either directly or transitively.
                    warnAboutCordaArtifacts("net.corda", artifacts)
                    warnAboutCordaArtifacts("com.r3.corda", artifacts)
                }
            + getCordappArtifacts(configurations)
            - cordaDeps.resolveFor(packagingConfiguration)
        ).toFiles()

        // Separate out any of these jars which we want to embed instead.
        val embeddedDeps = configurations.getByName(CORDA_EMBEDDED_CONFIGURATION_NAME).allDependencies
        val cpkFiles = packageFiles - embeddedDeps.resolveFor(packagingConfiguration).toFiles()
        _embeddedJars.apply {
            setFrom(packageFiles - cpkFiles)
            disallowChanges()
        }
        return cpkFiles
    }

    private fun Collection<ResolvedArtifact>.toFiles(): Set<File> = mapTo(LinkedHashSet(), ResolvedArtifact::getFile)

    private fun Collection<Dependency>.resolveFor(configuration: ResolvedConfiguration): Set<ResolvedArtifact> {
        return configuration.getFirstLevelModuleDependencies { contains(it) }
            .flatMapTo(LinkedHashSet(), ResolvedDependency::getAllModuleArtifacts)
    }

    // Resolve transitive dependencies for each CorDapp individually.
    private fun getCordappArtifacts(configurations: ConfigurationContainer): Set<ResolvedArtifact> {
        return configurations.getByName(CORDAPP_CONFIGURATION_NAME)
            .allDependencies
            .flatMapTo(LinkedHashSet()) {
                configurations.detachedConfiguration(it).resolvedConfiguration.resolvedArtifacts
            }
    }

    private fun warnAboutCordaArtifacts(packageName: String, artifacts: Iterable<ResolvedArtifact>) {
        val nameLength = packageName.length
        for (artifact in artifacts) {
            val group = artifact.moduleVersion.id.group
            if (group.startsWith(packageName)) {
                when {
                    nameLength == group.length ->
                        throw InvalidUserDataException("CorDapp must not contain '${artifact.moduleVersion}' (${artifact.file.name})")
                    group.length > nameLength && group[nameLength] == '.' ->
                        logger.warn("You appear to have included a Corda platform component '${artifact.moduleVersion}' (${artifact.file.name})."
                            + "You probably want to use either the $CORDA_PROVIDED_CONFIGURATION_NAME or the $CORDAPP_CONFIGURATION_NAME configuration here. "
                            + "See http://docs.corda.net/cordapp-build-systems.html"
                    )
                }
            }
        }
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
    private fun MessageDigest.hashFor(file: File): ByteArray {
        file.inputStream().use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length = it.read(buffer)
                if (length == EOF) {
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

private class DependencyCollector(excludeName: String) {
    private val excludeNames = mutableSetOf(excludeName)

    fun collectFrom(source: Configuration): Set<Dependency> {
        val result = mutableSetOf<Dependency>()
        collectFrom(source, result)
        return result
    }

    private fun collectFrom(source: Configuration, result: MutableSet<Dependency>) {
        if (!excludeNames.add(source.name)) {
            return
        }
        result.addAll(source.dependencies)
        for (parent in source.extendsFrom) {
            collectFrom(parent, result)
        }
    }
}
