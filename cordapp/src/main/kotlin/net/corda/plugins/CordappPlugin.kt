package net.corda.plugins

import net.corda.plugins.SignJar.Companion.sign
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.java.archives.Attributes
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.jvm.tasks.Jar
import org.gradle.util.GradleVersion
import java.io.File
import java.util.Collections.unmodifiableSet
import javax.inject.Inject

/**
 * The cordapp plugin will turn a project into a CorDapp project which builds CorDapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
@Suppress("Unused", "UnstableApiUsage")
class CordappPlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val archiveOps: ArchiveOperations,
    private val softwareComponentFactory: SoftwareComponentFactory
): Plugin<Project> {
    private companion object {
        private const val CORDA_CORDAPP_CONFIGURATION_NAME = "cordaCordapp"
        private const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
        private const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
        private const val CORDAPP_CONFIGURATION_NAME = "cordapp"
        private const val CORDAPP_COMPONENT_NAME = "cordapp"
        private const val CORDAPP_EXTENSION_NAME = "cordapp"
        private const val MIN_GRADLE_VERSION = "7.0"
        private const val UNKNOWN = "Unknown"

        private val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
            "org.jetbrains.kotlin" to "kotlin-stdlib",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jre8",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jdk8",
            "org.jetbrains.kotlin" to "kotlin-reflect",
            "co.paralleluniverse" to "quasar-core"
        ))
    }

    /**
     * CorDapp's information.
     */
    internal lateinit var cordapp: CordappExtension
        private set

    override fun apply(project: Project) {
        project.logger.info("Configuring {} as a CorDapp", project.name)

        if (GradleVersion.current() < GradleVersion.version(MIN_GRADLE_VERSION)) {
            throw GradleException("Gradle versionId ${GradleVersion.current().version} is below the supported minimum versionId $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "implementation", "compileOnly" and "runtimeOnly" configurations.
        project.pluginManager.apply(JavaLibraryPlugin::class.java)
        with(project.configurations) {
            getByName(IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(
                createBasicConfiguration(CORDAPP_CONFIGURATION_NAME),
                createBasicConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME)
            )
            getByName(RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(
                createBasicConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
            )
            val cordaCordapp = create(CORDA_CORDAPP_CONFIGURATION_NAME) {
                it.attributes { attr ->
                    attr.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, JAR))
                }
                it.isCanBeResolved = false
            }

            // Create a new software component for publishing this CorDapp.
            val cordappComponent = softwareComponentFactory.adhoc(CORDAPP_COMPONENT_NAME).also { adhoc ->
                adhoc.addVariantsFromConfiguration(cordaCordapp) {}
            }
            project.components.add(cordappComponent)
        }

        cordapp = project.extensions.create(CORDAPP_EXTENSION_NAME, CordappExtension::class.java, objects)
        configurePomCreation(project)
        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its SHA-256 hash is stable!
     */
    private fun configureCordappJar(project: Project) {
        val cordappTask = project.tasks.register("configureCordappFatJar")
        val configurations = project.configurations
        val groupProvider = project.provider {
            project.group.toString()
        }

        val jarTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java) { task ->
            task.inputs.nested(CORDAPP_EXTENSION_NAME, cordapp)
            task.dependsOn(cordappTask)
            task.fileMode = Integer.parseInt("444", 8)
            task.dirMode = Integer.parseInt("555", 8)
            task.manifestContentCharset = "UTF-8"
            task.isPreserveFileTimestamps = false
            task.isReproducibleFileOrder = true
            task.entryCompression = DEFLATED
            task.includeEmptyDirs = false
            task.isCaseSensitive = true
            task.isZip64 = false
            task.doFirst {
                val attributes = task.manifest.attributes
                // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
                if (cordapp.contract.isEmpty() && cordapp.workflow.isEmpty()) {
                    it.logger.warn("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
                } else {
                    configureCordappAttributes(groupProvider, task, attributes)
                }
            }.doLast {
                if (cordapp.signing.enabled.get()) {
                    it.sign(cordapp.signing, it.outputs.files.singleFile)
                } else {
                    it.logger.info("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                }
            }
        }

        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        cordappTask.configure { task ->
            task.description = "Computes this CorDapp's dependencies."
            task.group = CORDAPP_TASK_GROUP
            task.dependsOn(configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME).buildDependencies)
            task.doLast {
                jarTask.get().from(getDirectNonCordaDependencies(configurations, it.logger).map { file ->
                    it.logger.info("CorDapp dependency: {}", file.name)
                    archiveOps.zipTree(file)
                }).apply {
                    exclude("META-INF/*.SF")
                    exclude("META-INF/*.EC")
                    exclude("META-INF/*.DSA")
                    exclude("META-INF/*.RSA")
                    exclude("META-INF/*.MF")
                    exclude("META-INF/LICENSE*")
                    exclude("META-INF/NOTICE*")
                    exclude("META-INF/INDEX.LIST")
                }
            }
        }

        project.artifacts.add(CORDA_CORDAPP_CONFIGURATION_NAME, jarTask)
    }

    private fun configureCordappAttributes(groupProvider: Provider<String>, jarTask: Jar, attributes: Attributes) {
        val defaultName = "${groupProvider.get()}.${jarTask.archiveBaseName.get()}"
        // Corda 4 attributes support
        if (!cordapp.contract.isEmpty()) {
            attributes["Cordapp-Contract-Name"] = cordapp.contract.name.getOrElse(defaultName)
            attributes["Cordapp-Contract-Version"] = checkCorDappVersionId(cordapp.contract.versionId)
            attributes["Cordapp-Contract-Vendor"] = cordapp.contract.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Contract-Licence"] = cordapp.contract.licence.getOrElse(UNKNOWN)
        }
        if (!cordapp.workflow.isEmpty()) {
            attributes["Cordapp-Workflow-Name"] = cordapp.workflow.name.getOrElse(defaultName)
            attributes["Cordapp-Workflow-Version"] = checkCorDappVersionId(cordapp.workflow.versionId)
            attributes["Cordapp-Workflow-Vendor"] = cordapp.workflow.vendor.getOrElse(UNKNOWN)
            attributes["Cordapp-Workflow-Licence"] = cordapp.workflow.licence.getOrElse(UNKNOWN)
        }
        val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
        attributes["Target-Platform-Version"] = targetPlatformVersion
        attributes["Min-Platform-Version"] = minimumPlatformVersion
        if (cordapp.sealing.enabled.get()) {
            attributes["Sealed"] = "true"
        }
    }

    private fun configurePomCreation(project: Project) {
        with(project.components) {
            matching { it.name != CORDAPP_COMPONENT_NAME }.all { remove(it) }
        }
        project.tasks.withType(GenerateModuleMetadata::class.java).configureEach { task ->
            // We cannot fix Gradle's own module metadata either.
            task.enabled = false
        }
    }

    private fun calculateExcludedDependencies(configurations: ConfigurationContainer): Set<Dependency> {
        //TODO if we intend cordapp jars to define transitive dependencies instead of just being fat
        //we need to use the final artifact name, not the project name
        //for example, project(":core") needs to be translated into net.corda:corda-core

        return configurations.getByName(CORDAPP_CONFIGURATION_NAME).allDependencies +
            configurations.getByName(CORDA_PROVIDED_CONFIGURATION_NAME).allDependencies +
            configurations.getByName(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME).allDependencies
    }

    private fun getDirectNonCordaDependencies(configurations: ConfigurationContainer, logger: Logger): Set<File> {
        logger.info("Finding direct non-Corda dependencies for inclusion in CorDapp JAR")

        val runtimeConfiguration = configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        // The direct dependencies of this project
        val excludeDeps = calculateExcludedDependencies(configurations)
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        val filteredDeps = directDeps.filter { dep ->
            HARDCODED_EXCLUDES.none { exclude -> (exclude.first == dep.group) && (exclude.second == dep.name) }
        }
        filteredDeps.forEach {
            // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
            val group = it.group ?: ""
            if (group.startsWith("net.corda.") || group.startsWith("com.r3.corda.")) {
                logger.warn(
                    "You appear to have included a Corda platform component ({}) using an 'api', 'implementation', or 'runtimeOnly' dependency. " +
                        "This can cause node stability problems. Please use 'cordaProvided' or 'cordaRuntimeOnly' instead. " +
                        "See http://docs.corda.net/cordapp-build-systems.html", it
                )
            } else {
                logger.info("Including dependency in CorDapp JAR: {}", it)
            }
        }
        return filteredDeps.toUniqueFiles(runtimeConfiguration) - excludeDeps.toUniqueFiles(runtimeConfiguration)
    }

    private fun checkPlatformVersionInfo(): Pair<Int, Int> {
        // If the minimum platform version is not set, default to 1.
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion.getOrElse(1)
        val targetPlatformVersion = cordapp.targetPlatformVersion.orNull
            ?: throw InvalidUserDataException("CorDapp `targetPlatformVersion` was not specified in the `cordapp` metadata section.")
        if (targetPlatformVersion < 1) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than 1.")
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than the `minimumPlatformVersion` ($minimumPlatformVersion)")
        }
        return Pair(targetPlatformVersion, minimumPlatformVersion)
    }

    private fun checkCorDappVersionId(versionId: Property<Int>): Int {
        if (!versionId.isPresent)
            throw InvalidUserDataException("CorDapp `versionId` was not specified in the associated `contract` or `workflow` metadata section. Please specify a whole number starting from 1.")
        val value = versionId.get()
        if (value < 1) {
            throw InvalidUserDataException("CorDapp `versionId` must not be smaller than 1.")
        }
        return value
    }

    private fun Iterable<Dependency>.toUniqueFiles(configuration: Configuration): Set<File> {
        return flatMapTo(LinkedHashSet()) { configuration.files(it) }
    }
}
