package net.corda.plugins.cpk

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import java.io.File
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class DependencyCalculator @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    private companion object {
        const val NON_CORDA = false
        const val CORDA = true
    }

    init {
        description = "Computes this CorDapp's dependencies."
        group = GROUP_NAME

        // Force this task to execute!
        outputs.upToDateWhen { false }
    }

    private val _dependencies: ConfigurableFileCollection = objects.fileCollection()
    val dependencies: Provider<Set<FileSystemLocation>>
        @OutputFiles
        get() = _dependencies.elements

    private val _embeddedJars: ConfigurableFileCollection = objects.fileCollection()
    val embeddedJars: Provider<Set<FileSystemLocation>>
        @OutputFiles
        get() = _embeddedJars.elements

    private val _externalJars: ConfigurableFileCollection = objects.fileCollection()
    val externalJars: Provider<Set<FileSystemLocation>>
        @OutputFiles
        get() = _externalJars.elements

    /**
     * Sets this task's dependencies; to be invoked when the task is configured.
     * (Deliberately NOT invoking this from the constructor because I'm unclear
     * on where [org.gradle.api.Task] construction fits into the Gradle lifecycle.
     */
    fun dependsOnCordappConfigurations() {
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

    @TaskAction
    fun calculate() {
        val configurations = project.configurations

        // Compute the (unresolved) dependencies on the runtime classpath
        // that the user has selected for this CPK archive. We ignore any
        // dependencies from the cordaRuntimeOnly configuration because
        // these will be provided by Corda. Also ignore anything from the
        // cordaEmbedded configuration, because these will be added to the
        // bundle classpath.
        val runtimeConfiguration = configurations.getByName(CORDAPP_PACKAGING_CONFIGURATION_NAME)
        val runtimeDeps = DependencyCollector(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME, CORDA_EMBEDDED_CONFIGURATION_NAME)
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
            nonCordaDeps.resolveWithoutCorda(packagingConfiguration)
            + getCordappArtifacts(configurations)
            - cordaDeps.resolveFor(packagingConfiguration)
        ).toFiles() + nonCordaDeps.toSelfResolvingFiles()

        // Separate out any jars which we want to embed instead.
        // Avoid embedding anything which another CorDapp depends on.
        val embeddedDeps = configurations.getByName(CORDA_EMBEDDED_CONFIGURATION_NAME).allDependencies - runtimeDeps
        val embeddedFiles = embeddedDeps.resolveWithoutCorda(packagingConfiguration).toFiles() + embeddedDeps.toSelfResolvingFiles() - packageFiles
       _embeddedJars.apply {
            setFrom(embeddedFiles)
            disallowChanges()
        }
        _dependencies.apply {
            setFrom(packageFiles)
            disallowChanges()
        }

        // Finally, work out which jars from the compile classpath are excluded from the runtime classpath.
        // We still ignore anything that was for "compile only", because we only want to validate packages
        // that will be available at runtime.
        val compileConfiguration = configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration
        val cordaFiles = cordaDeps.resolveFor(compileConfiguration).toFiles()
        val providedDeps = configurations.getByName(CORDA_PROVIDED_CONFIGURATION_NAME).allDependencies
        val providedFiles = providedDeps.resolvedFilesFor(compileConfiguration)
        _externalJars.apply {
            setFrom(providedFiles, cordaFiles)
            disallowChanges()
        }
    }

    private fun Collection<Dependency>.resolvedFilesFor(configuration: ResolvedConfiguration): Set<File> {
        return resolveFor(configuration).toFiles() + toSelfResolvingFiles()
    }

    private fun Collection<ResolvedArtifact>.toFiles(): Set<File> = mapTo(LinkedHashSet(), ResolvedArtifact::getFile)

    private fun Collection<Dependency>.toSelfResolvingFiles(): Set<File>
        = filterIsInstance(SelfResolvingDependency::class.java)
            .flatMapTo(LinkedHashSet(), SelfResolvingDependency::resolve)

    private fun Collection<Dependency>.resolveFor(configuration: ResolvedConfiguration): Set<ResolvedArtifact> {
        return configuration.getFirstLevelModuleDependencies { contains(it) }
            .flatMapTo(LinkedHashSet(), ResolvedDependency::getAllModuleArtifacts)
    }

    private fun Collection<Dependency>.resolveWithoutCorda(configuration: ResolvedConfiguration): List<ResolvedArtifact> {
        return resolveFor(configuration)
            .filterNot { artifact -> isCordaProvided(artifact.moduleVersion.id) }
            .also { artifacts ->
                // Corda artifacts should not be included, either directly or transitively.
                warnAboutCordaArtifacts("net.corda", artifacts)
                warnAboutCordaArtifacts("com.r3.corda", artifacts)
            }
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
                        logger.warn("You appear to have included a Corda platform component '${artifact.moduleVersion}' (${artifact.file.name}). "
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
}

private class DependencyCollector(vararg excludeName: String) {
    private val excludeNames = mutableSetOf(*excludeName)

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
