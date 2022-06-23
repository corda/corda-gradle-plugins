package net.corda.plugins.cpk

import aQute.bnd.gradle.BndBuilderPlugin
import aQute.bnd.gradle.BundleTaskExtension
import net.corda.plugins.cpk.SignJar.Companion.sign
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.ProjectLayout
import org.gradle.api.java.archives.Attributes
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS
import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.util.GradleVersion
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import java.util.Collections.unmodifiableList
import java.util.Properties
import javax.inject.Inject
import kotlin.math.max

/**
 * Generate a new CPK format CorDapp for use in Corda.
 */
@Suppress("Unused", "UnstableApiUsage")
class CordappPlugin @Inject constructor(
    private val layouts: ProjectLayout,
    private val softwareComponentFactory: SoftwareComponentFactory
): Plugin<Project> {
    private companion object {
        private const val UNKNOWN_PLATFORM_VERSION = -1
        private const val CORDA_PLATFORM_VERSION = "Corda-Platform-Version"
        private const val PLUGIN_PROPERTIES = "cordapp-cpk.properties"
        private const val DEPENDENCY_CONSTRAINTS_TASK_NAME = "cordappDependencyConstraints"
        private const val DEPENDENCY_CALCULATOR_TASK_NAME = "cordappDependencyCalculator"
        private const val CPK_DEPENDENCIES_TASK_NAME = "cordappCPKDependencies"
        private const val VERIFY_LIBRARIES_TASK_NAME = "verifyLibraries"
        private const val VERIFY_BUNDLE_TASK_NAME = "verifyBundle"
        private const val CORDAPP_COMPONENT_NAME = "cordapp"
        private const val CORDAPP_EXTENSION_NAME = "cordapp"
        private const val OSGI_EXTENSION_NAME = "osgi"
        private const val MIN_GRADLE_VERSION = "7.2"
        private const val UNKNOWN = "Unknown"

        private val CORDAPP_BUILD_CONFIGURATIONS: List<String> = unmodifiableList(listOf(
            /**
             * Every CorDapp configuration is a super-configuration of at least one of these
             * configurations. Hence every [ProjectDependency][org.gradle.api.artifacts.ProjectDependency]
             * needed to build this CorDapp should exist somewhere beneath their umbrella.
             */
            CORDAPP_PACKAGING_CONFIGURATION_NAME,
            CORDAPP_EXTERNAL_CONFIGURATION_NAME
        ))

        private val pluginProperties: Properties get() {
            return Properties().also { props ->
                this::class.java.getResourceAsStream(PLUGIN_PROPERTIES)?.use(props::load)
            }
        }

        private fun Properties.getValue(name: String): String {
            return getProperty(name) ?: throw InvalidUserCodeException("$name missing from CPK plugin")
        }
    }

    /**
     * CorDapp's information.
     */
    private lateinit var cordapp: CordappExtension

    override fun apply(project: Project) {
        project.logger.info("Configuring {} as a CorDapp", project.name)

        if (GradleVersion.current() < GradleVersion.version(MIN_GRADLE_VERSION)) {
            throw GradleException("Gradle version ${GradleVersion.current().version} is below the supported minimum version $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: $CORDAPP_DOCUMENTATION_URL")
        }

        with(project.pluginManager) {
            // Apply the 'java-library' plugin on the assumption that we're building a JAR.
            // This will also create the "api", "implementation", "compileOnly" and "runtimeOnly" configurations.
            apply("java-library")

            // Apply the Bnd "builder" plugin to generate OSGi metadata for the CorDapp.
            apply(BndBuilderPlugin::class.java)
        }

        // Create our plugin's "cordapp" extension.
        cordapp = with(pluginProperties) {
            project.extensions.create(CORDAPP_EXTENSION_NAME, CordappExtension::class.java, getValue("osgiVersion"), getValue("bndVersion"))
        }

        project.configurations.apply {
            // Generator object for variant attributes. We need this to ensure
            // Gradle resolves project dependencies into the correct artifacts.
            val attributor = Attributor(project.objects)

            // Create the outgoing configuration, and add it to custom software component.
            val cordaCPK = create(CORDA_CPK_CONFIGURATION_NAME)
                .attributes(attributor::forCpk)
                .also {
                    it.isCanBeResolved = false
                }
            val component = softwareComponentFactory.adhoc(CORDAPP_COMPONENT_NAME).also { adhoc ->
                adhoc.addVariantsFromConfiguration(getByName(API_ELEMENTS_CONFIGURATION_NAME), CordappVariantMapping("compile"))
                adhoc.addVariantsFromConfiguration(getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME), CordappVariantMapping("runtime"))
                adhoc.addVariantsFromConfiguration(cordaCPK) {}
            }
            project.components.add(component)

            // This definition of cordaRuntimeOnly must be kept aligned with the one in the quasar-utils plugin.
            val cordaRuntimeOnly = createBasicConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
                .setDescription("Runtime-only dependencies which do not belong to this CorDapp.")
                .setTransitive(false)
            getByName(RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(cordaRuntimeOnly)

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).withDependencies { dependencies ->
                val bndDependency = project.dependencies.create("biz.aQute.bnd:biz.aQute.bnd.annotation:" + cordapp.bndVersion.get())
                dependencies.add(bndDependency)

                val osgiDependency = project.dependencies.create("org.osgi:osgi.annotation:" + cordapp.osgiVersion.get())
                dependencies.add(osgiDependency)
            }

            // We will ALWAYS want to compile against bundles, and not classes.
            // Bnd probably sets this attribute already, but still set it anyway.
            getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).attributes(attributor::forJar)

            val cordappCfg = createCompileConfiguration(CORDAPP_CONFIGURATION_NAME)
                .setDescription("The CorDapps that this CorDapp directly depends on.")

            // The "cordapp" and "cordaProvided" configurations are "compile only",
            // which causes their dependencies to be excluded from the published
            // POM. This also means that CPK dependencies will not be transitive
            // by default, and so we must implement a way of fixing this ourselves.
            val collector = CordappDependencyCollector(
                configurations = this,
                dependencyHandler = project.dependencies,
                attributor = attributor,
                logger = project.logger
            )
            val allCordapps = createCompileConfiguration(ALL_CORDAPPS_CONFIGURATION_NAME)
                .setDescription("Every CorDapp this CorDapp depends on, either directly or indirectly.")
                .extendsFrom(cordappCfg)
                .withDependencies { dependencies ->
                    collector.collect()
                    dependencies.addAll(collector.cordappDependencies)
                    dependencies.filterIsInstance(ModuleDependency::class.java)
                        .filterNot(::isPlatformModule)
                        .forEach { dep ->
                            // Ensure that none of these dependencies is transitive. This will prevent
                            // Gradle from adding any of these CorDapps' private library dependencies
                            // to our own compile classpath.
                            // WE ARE MUTATING THESE DEPENDENCIES FOR EVERY CONFIGURATION THEY APPEAR IN!
                            if (dep.isTransitive) {
                                // Synchronised to be sure! I don't know how multi-threaded Gradle is.
                                synchronized(dep) {
                                    if (dep is ProjectDependency) {
                                        // Preserve the original setting so that
                                        // CordappDependencyCollector can use it.
                                        dep.attributes(attributor::forTransitive)
                                    }
                                    dep.isTransitive = false
                                }
                            }

                            // We also need to GUARANTEE that Gradle uses the jar artifact here.
                            // Only the jar contains the OSGi metadata we need.
                            if (dep is ProjectDependency) {
                                dep.attributes(attributor::forJar)
                            }
                        }
                }

            // This definition of cordaProvided must be kept aligned with the one in the quasar-utils plugin.
            val cordaProvided = createCompileConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME)
                .setDescription("Compile-only dependencies which Corda will provide at runtime.")

            // Unlike cordaProvided dependencies, cordaPrivateProvided ones will not be
            // added to the compile classpath of any CPKs that will depend on this CPK.
            // In other words, they will not be included in this CPK's companion POM.
            val cordaPrivate = createCompileConfiguration(CORDA_PRIVATE_CONFIGURATION_NAME)
                .setDescription("Corda-provided dependencies which are only available to this CorDapp.")

            val allProvided = createCompileConfiguration(CORDA_ALL_PROVIDED_CONFIGURATION_NAME)
                .setDescription("Every Corda-provided dependency, including private and transitive ones.")
                .extendsFrom(cordaProvided, cordaPrivate)
                .withDependencies { dependencies ->
                    collector.collect()
                    dependencies.addAll(collector.providedDependencies)
                }

            // Embedded dependencies should not appear in the CorDapp's published POM.
            val cordaEmbedded = createCompileConfiguration(CORDA_EMBEDDED_CONFIGURATION_NAME)
                .setDescription("These dependencies are added to the CorDapp's Bundle-Classpath.")

            // We need to resolve the contents of our CPK file based on
            // both the runtimeElements and cordaEmbedded configurations.
            // This won't happen by default because cordaEmbedded is a
            // "compile only" configuration.
            create(CORDAPP_PACKAGING_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(cordaEmbedded, getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME))
                .attributes(attributor::forRuntimeClasspath)
                .isCanBeConsumed = false

            create(CORDAPP_EXTERNAL_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(allProvided, allCordapps)
                .attributes(attributor::forCompileClasspath)
                .isCanBeConsumed = false
        }

        // We need to perform some extra work on the root project to support publication.
        project.pluginManager.withPlugin("maven-publish", CordappPublishing(project.rootProject))

        configureCordappTasks(project)
    }

    /**
     * Configures this project's JAR as a CorDapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its hash is stable!
     */
    private fun configureCordappTasks(project: Project) {
        val calculatorTask = project.tasks.register(DEPENDENCY_CALCULATOR_TASK_NAME, DependencyCalculator::class.java) { task ->
            task.setDependsOn(
                /**
                 * Every CorDapp configuration is a super-configuration of at least one of these
                 * configurations. Hence every [ProjectDependency][org.gradle.api.artifacts.ProjectDependency]
                 * needed to build this CorDapp should exist somewhere beneath their umbrella.
                 */
                CORDAPP_BUILD_CONFIGURATIONS.map(project.configurations::getByName)
                    .mapTo(LinkedHashSet(), Configuration::getBuildDependencies)
            )
        }

        /**
         * Generate an extra resource file containing constraints for all of this CorDapp's dependencies.
         */
        val constraintsDir = layouts.buildDirectory.dir("generated-constraints")
        val constraintsTask = project.tasks.register(DEPENDENCY_CONSTRAINTS_TASK_NAME, DependencyConstraintsTask::class.java) { task ->
            task.setLibrariesFrom(calculatorTask)
            task.constraintsDir.set(constraintsDir)
            task.hashAlgorithm.set(cordapp.hashAlgorithm)
        }

        /**
         * Generate an extra resource file listing this CorDapp's CPK dependencies.
         */
        val cpkDependenciesDir = layouts.buildDirectory.dir("cpk-dependencies")
        val cpkDependenciesTask = project.tasks.register(CPK_DEPENDENCIES_TASK_NAME, CPKDependenciesTask::class.java) { task ->
            task.setCPKsFrom(calculatorTask)
            task.outputDir.set(cpkDependenciesDir)
            task.hashAlgorithm.set(cordapp.hashAlgorithm)
        }

        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        sourceSets.getByName(MAIN_SOURCE_SET_NAME) { main ->
            main.output.apply {
                dir(mapOf("builtBy" to constraintsTask), constraintsDir)
                dir(mapOf("builtBy" to cpkDependenciesTask), cpkDependenciesDir)
            }
        }

        val objects = project.objects
        val jarTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java) { jar ->
            jar.inputs.nested(CORDAPP_EXTENSION_NAME, cordapp)

            val osgi = jar.extensions.create(OSGI_EXTENSION_NAME, OsgiExtension::class.java, objects, jar)
            osgi.embed(calculatorTask.flatMap(DependencyCalculator::embeddedJars))
            jar.inputs.nested(OSGI_EXTENSION_NAME, osgi)

            with(jar.extensions.getByType(BundleTaskExtension::class.java)) {
                // Add jars which have been migrated off the Bundle-Classpath
                // back into Bnd's regular classpath.
                classpath(calculatorTask.flatMap(DependencyCalculator::unbundledJars))

                // Add a Bnd instruction to export the set of observed package names.
                bnd(osgi.exports)

                // Add Bnd instructions to embed requested jars into this bundle.
                bnd(osgi.embeddedJars)

                // Add a Bnd instruction for explicit package imports.
                bnd(osgi.imports)

                // Add Bnd instructions to scan for any contracts, flows, schemas etc.
                bnd(osgi.scanCordaClasses)

                // Accessing the Gradle [Project] during the task execution
                // phase is incompatible with Gradle's configuration cache.
                // Prevent this task from accessing the project's properties.
                properties.convention(emptyMap())
            }

            val allCordaProvided = objects.fileCollection()
                .from(calculatorTask.flatMap(DependencyCalculator::providedJars))
            val allCordapps = objects.fileCollection().from(
                calculatorTask.flatMap(DependencyCalculator::remoteCordapps),
                calculatorTask.flatMap(DependencyCalculator::projectCordapps)
            )
            jar.doFirst { t ->
                t as Jar
                t.fileMode = Integer.parseInt("444", 8)
                t.dirMode = Integer.parseInt("555", 8)
                t.manifestContentCharset = "UTF-8"
                t.isPreserveFileTimestamps = false
                t.isReproducibleFileOrder = true
                t.entryCompression = DEFLATED
                t.duplicatesStrategy = FAIL
                t.includeEmptyDirs = false
                t.isCaseSensitive = true
                t.isZip64 = true

                val attributes = t.manifest.attributes
                // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
                if (cordapp.contract.isEmpty && cordapp.workflow.isEmpty) {
                    throw InvalidUserDataException("CorDapp metadata not defined for this Gradle build file. See $CORDAPP_DOCUMENTATION_URL")
                }

                // Compute the maximum platform version used by any "corda-provided"
                // dependencies, or by any CorDapp dependencies.
                val platformVersion = max(
                    allCordaProvided.maxOf(CORDA_PLATFORM_VERSION) ?: UNKNOWN_PLATFORM_VERSION,
                    allCordapps.maxOf(CORDAPP_PLATFORM_VERSION) ?: UNKNOWN_PLATFORM_VERSION
                )
                if (platformVersion > UNKNOWN_PLATFORM_VERSION) {
                    attributes[CORDAPP_PLATFORM_VERSION] = platformVersion
                }

                configureCordappAttributes(osgi.symbolicName.get(), attributes)
            }.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    t.sign(cordapp.signing.options, (t as Jar).archiveFile.get().asFile)
                } else {
                    t.logger.lifecycle("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                }
            }
        }

        /**
         * Check that all of this CPK's libraries are actually bundles.
         */
        val verifyLibraries = project.tasks.register(VERIFY_LIBRARIES_TASK_NAME, VerifyLibraries::class.java) { verify ->
            verify.setDependenciesFrom(calculatorTask)
        }

        /**
         * Ask Bnd to "sanity-check" this new bundle.
         */
        val verifyBundle = project.tasks.register(VERIFY_BUNDLE_TASK_NAME, VerifyBundle::class.java) { verify ->
            verify.bundle.set(jarTask.flatMap(Jar::getArchiveFile))
            verify.setDependenciesFrom(calculatorTask)

            // Disable this task if the jar task is disabled.
            project.gradle.taskGraph.whenReady(copyJarEnabledTo(verify))
        }
        jarTask.configure { jar ->
            jar.dependsOn(verifyLibraries)
            jar.finalizedBy(verifyBundle)
        }

        /**
         * Package the CorDapp and all of its dependencies into a Jar archive with CPK extension.
         * The CPK artifact should have the same base-name and appendix as the CorDapp's [Jar] task.
         */
        val cpkTask = project.tasks.register(CPK_TASK_NAME, PackagingTask::class.java) { task ->
            task.inputs.nested("signing", cordapp.signing)
            task.mustRunAfter(verifyBundle)

            // Basic configuration of the packaging task.
            task.destinationDirectory.convention(jarTask.flatMap(Jar::getDestinationDirectory))
            task.archiveBaseName.convention(jarTask.flatMap(Jar::getArchiveBaseName))
            task.archiveAppendix.convention(jarTask.flatMap(Jar::getArchiveAppendix))
            task.archiveVersion.convention(jarTask.flatMap(Jar::getArchiveVersion))

            // Configure the CPK archive contents.
            task.setLibrariesFrom(constraintsTask)
            task.cordapp.set(jarTask.flatMap(Jar::getArchiveFile))
            task.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    t.sign(cordapp.signing.options, (t as PackagingTask).archiveFile.get().asFile)
                }
            }

            // Disable this task if the jar task is disabled.
            project.gradle.taskGraph.whenReady(copyJarEnabledTo(task))
        }

        with(project.artifacts) {
            add(ARCHIVES_CONFIGURATION, cpkTask)
            add(CORDA_CPK_CONFIGURATION_NAME, cpkTask)
        }
    }

    private fun configureCordappAttributes(symbolicName: String, attributes: Attributes) {
        attributes[BUNDLE_SYMBOLICNAME] = symbolicName

        if (!cordapp.contract.isEmpty) {
            val contractName = cordapp.contract.name.getOrElse(symbolicName)
            val vendor = cordapp.contract.vendor.getOrElse(UNKNOWN)
            val licence = cordapp.contract.licence.getOrElse(UNKNOWN)
            attributes[BUNDLE_NAME] = contractName
            attributes[BUNDLE_VENDOR] = vendor
            attributes[BUNDLE_LICENSE] = licence
            attributes[CORDAPP_CONTRACT_NAME] = contractName
            attributes[CORDAPP_CONTRACT_VERSION] = checkCorDappVersionId(cordapp.contract.versionId)
            attributes["Cordapp-Contract-Vendor"] = vendor
            attributes["Cordapp-Contract-Licence"] = licence
        }
        if (!cordapp.workflow.isEmpty) {
            val workflowName = cordapp.workflow.name.getOrElse(symbolicName)
            val vendor = cordapp.workflow.vendor.getOrElse(UNKNOWN)
            val licence = cordapp.workflow.licence.getOrElse(UNKNOWN)
            attributes[BUNDLE_NAME] = workflowName
            attributes[BUNDLE_VENDOR] = vendor
            attributes[BUNDLE_LICENSE] = licence
            attributes[CORDAPP_WORKFLOW_NAME] = workflowName
            attributes[CORDAPP_WORKFLOW_VERSION] = checkCorDappVersionId(cordapp.workflow.versionId)
            attributes["Cordapp-Workflow-Vendor"] = vendor
            attributes["Cordapp-Workflow-Licence"] = licence
        }

        val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
        attributes["Target-Platform-Version"] = targetPlatformVersion
        attributes["Min-Platform-Version"] = minimumPlatformVersion
        if (cordapp.sealing.get()) {
            attributes["Sealed"] = true
        }
    }

    private fun checkPlatformVersionInfo(): Pair<Int, Int> {
        // If the minimum platform version is not set, it defaults to X.
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion.get()
        val targetPlatformVersion: Int = cordapp.targetPlatformVersion.get()
        if (targetPlatformVersion < PLATFORM_VERSION_X) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than $PLATFORM_VERSION_X.")
        }
        if (targetPlatformVersion < minimumPlatformVersion) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than the `minimumPlatformVersion` ($minimumPlatformVersion)")
        }
        return Pair(targetPlatformVersion, minimumPlatformVersion)
    }

    private fun checkCorDappVersionId(versionId: Property<Int>): Int {
        if (!versionId.isPresent) {
            throw InvalidUserDataException("CorDapp `versionId` was not specified in the associated `contract` or `workflow` metadata section. Please specify a whole number starting from 1.")
        }
        val value = versionId.get()
        if (value < 1) {
            throw InvalidUserDataException("CorDapp `versionId` must not be smaller than 1.")
        }
        return value
    }
}

private class CordappVariantMapping(private val mavenScope: String) : Action<ConfigurationVariantDetails> {
    override fun execute(variant: ConfigurationVariantDetails) {
        if (variant.configurationVariant.artifacts.any { it.type in UNPUBLISHABLE_VARIANT_ARTIFACTS }) {
            variant.skip()
        }
        variant.mapToMavenScope(mavenScope)
    }
}

/**
 * Ensure that we add the [PublishAfterEvaluationHandler]
 * to the root project exactly once, regardless of how
 * many times this [Action] is invoked.
 */
private class CordappPublishing(private val rootProject: Project) : Action<AppliedPlugin> {
    @Suppress("SameParameterValue")
    private fun setExtraProperty(key: String): Boolean {
        val rootProperties = rootProject.extensions.extraProperties
        return synchronized(rootProperties) {
            if (rootProperties.has(key)) {
                false
            } else {
                rootProperties[key] = Any()
                true
            }
        }
    }

    override fun execute(plugin: AppliedPlugin) {
        if (setExtraProperty("_net_corda_cordapp_cpk_publish_")) {
            rootProject.gradle.projectsEvaluated(PublishAfterEvaluationHandler(rootProject))
        }
    }
}
