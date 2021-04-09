package net.corda.plugins.cpk

import aQute.bnd.gradle.BndBuilderPlugin
import aQute.bnd.gradle.BundleTaskConvention
import net.corda.plugins.cpk.SignJar.Companion.sign
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.ProjectLayout
import org.gradle.api.java.archives.Attributes
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.util.GradleVersion
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import java.util.Properties
import javax.inject.Inject

/**
 * Generate a new CPK format CorDapp for use in Corda.
 */
@Suppress("Unused", "UnstableApiUsage")
class CordappPlugin @Inject constructor(private val layouts: ProjectLayout): Plugin<Project> {
    private companion object {
        private const val BNDLIB_PROPERTIES = "META-INF/maven/biz.aQute.bnd/biz.aQute.bndlib/pom.properties"
        private const val DEPENDENCY_CONSTRAINTS_TASK_NAME = "cordappDependencyConstraints"
        private const val DEPENDENCY_CALCULATOR_TASK_NAME = "cordappDependencyCalculator"
        private const val CPK_DEPENDENCIES_TASK_NAME = "cordappCPKDependencies"
        private const val VERIFY_BUNDLE_TASK_NAME = "verifyBundle"
        private const val CORDAPP_EXTENSION_NAME = "cordapp"
        private const val OSGI_EXTENSION_NAME = "osgi"
        private const val MIN_GRADLE_VERSION = "6.6"
        private const val CPK_TASK_NAME = "cpk"
        private const val UNKNOWN = "Unknown"

        val Array<String>.packageRange: IntRange get() {
            val firstIdx = if (size > 2 && this[0] == "META-INF" && this[1] == "versions") { 3 } else { 0 }
            return firstIdx..size - 2
        }

        private val bndVersion: String get() {
            val properties = Properties().also { props ->
                this::class.java.classLoader.getResourceAsStream(BNDLIB_PROPERTIES)?.use(props::load)
            }
            return properties.getValue("version")
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
        project.logger.info("Configuring ${project.name} as a cordapp")

        if (GradleVersion.current() < GradleVersion.version(MIN_GRADLE_VERSION)) {
            throw GradleException("Gradle version ${GradleVersion.current().version} is below the supported minimum version $MIN_GRADLE_VERSION. Please update Gradle or consider using Gradle wrapper if it is provided with the project. More information about CorDapp build system can be found here: https://docs.corda.net/cordapp-build-systems.html")
        }

        // Apply the 'java-library' plugin on the assumption that we're building a JAR.
        // This will also create the "api", "implementation", "compileOnly" and "runtimeOnly" configurations.
        project.pluginManager.apply("java-library")

        // Apply the Bnd "builder" plugin to generate OSGi metadata for the CorDapp.
        project.pluginManager.apply(BndBuilderPlugin::class.java)

        // Create our plugin's "cordapp" extension.
        cordapp = project.extensions.create(CORDAPP_EXTENSION_NAME, CordappExtension::class.java, bndVersion)

        project.configurations.apply {
            // Strip unwanted transitive dependencies from incoming CorDapps.
            val cordappCfg = createCompileConfiguration(CORDAPP_CONFIGURATION_NAME).withDependencies { dependencies ->
                val excludeRules = HARDCODED_EXCLUDES.map { exclude ->
                    mapOf("group" to exclude.first, "module" to exclude.second)
                }
                dependencies.filterIsInstance(ModuleDependency::class.java).forEach { dep ->
                    excludeRules.forEach { rule -> dep.exclude(rule) }
                }
            }
            val cordaProvided = createCompileConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME)
            createRuntimeOnlyConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
            maybeCreate(CORDA_CPK_CONFIGURATION_NAME).isCanBeResolved = false

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).withDependencies { dependencies ->
                val bndDependency = project.dependencies.create("biz.aQute.bnd:biz.aQute.bnd.annotation:" + cordapp.bndVersion.get())
                dependencies.add(bndDependency)
            }

            // Embedded dependencies should not appear in the CorDapp's published POM.
            val cordaEmbedded = createCompileConfiguration(CORDA_EMBEDDED_CONFIGURATION_NAME)

            // The "cordapp" and "cordaProvided" configurations are "compile only",
            // which causes their dependencies to be excluded from the published
            // POM. This also means that CPK dependencies will not be transitive
            // by default, and so we must implement a way of fixing this ourselves.
            val collector = CordappDependencyCollector(this, project.dependencies, project.logger)
            createCompileConfiguration(ALL_CORDAPPS_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(cordappCfg)
                .withDependencies { dependencies ->
                    collector.collect()
                    dependencies.addAll(collector.cordappDependencies)
                }
            createCompileConfiguration(CORDA_ALL_PROVIDED_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(cordaProvided)
                .withDependencies { dependencies ->
                    collector.collect()
                    dependencies.addAll(collector.providedDependencies)
                }

            // We need to resolve the contents of our CPK file based on
            // both the runtimeElements and cordaEmbedded configurations.
            // This won't happen by default because cordaEmbedded is a
            // "compile only" configuration.
            create(CORDAPP_PACKAGING_CONFIGURATION_NAME).setVisible(false)
                .extendsFrom(getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME))
                .extendsFrom(cordaEmbedded)
                .isCanBeConsumed = false
        }

        // We need to perform some extra work on the root project to support publication.
        project.pluginManager.withPlugin("maven-publish", CordappPublishing(project.rootProject))

        configureCordappTasks(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its SHA-256 hash is stable!
     */
    private fun configureCordappTasks(project: Project) {
        val calculatorTask = project.tasks.register(DEPENDENCY_CALCULATOR_TASK_NAME, DependencyCalculator::class.java) { task ->
            task.dependsOnCordappConfigurations()
        }

        /**
         * Generate an extra resource file containing constraints for all of this CorDapp's dependencies.
         */
        val constraintsDir = layouts.buildDirectory.dir("generated-constraints")
        val constraintsTask = project.tasks.register(DEPENDENCY_CONSTRAINTS_TASK_NAME, DependencyConstraintsTask::class.java) { task ->
            task.setLibrariesFrom(calculatorTask)
            task.constraintsDir.set(constraintsDir)
        }

        /**
         * Generate an extra resource file listing this CorDapp's CPK dependencies.
         */
        val cpkDependenciesDir = layouts.buildDirectory.dir("cpk-dependencies")
        val cpkDependenciesTask = project.tasks.register(CPK_DEPENDENCIES_TASK_NAME, CPKDependenciesTask::class.java) { task ->
            task.setCPKsFrom(calculatorTask)
            task.outputDir.set(cpkDependenciesDir)
        }

        val sourceSets = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets
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
            jar.inputs.nested(OSGI_EXTENSION_NAME, osgi)
            osgi.embed(calculatorTask.flatMap(DependencyCalculator::embeddedJars))

            val noPackages = objects.setProperty(String::class.java)
                .apply(SetProperty<String>::disallowChanges)
            val autoPackages = objects.setProperty(String::class.java)
            osgi.exportPackages(osgi.autoExport.flatMap { isAuto ->
                if (isAuto) {
                    autoPackages
                } else {
                    noPackages
                }
            })

            // Install a "listener" for files copied into the CorDapp jar.
            // We will extract the names of the non-empty packages inside
            // this jar as we go...
            jar.rootSpec.eachFile { file ->
                if (!file.isDirectory) {
                    val elements = file.relativePath.segments
                    val packageRange = elements.packageRange
                    if (!packageRange.isEmpty()) {
                        val packageName = elements.slice(packageRange)
                        if (packageName[0] != "META-INF" && packageName[0] != "OSGI-INF" && packageName.isJavaIdentifiers) {
                            autoPackages.add(packageName.joinToString("."))
                        }
                    }
                }
            }

            with(jar.convention.getPlugin(BundleTaskConvention::class.java)) {
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
            }

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
                    throw InvalidUserDataException("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
                }

                configureCordappAttributes(osgi.symbolicName.get(), attributes)
            }.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    t.sign(cordapp.signing, (t as Jar).archiveFile.get().asFile)
                } else {
                    t.logger.lifecycle("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                }
            }
        }

        /**
         * Ask Bnd to "sanity-check" this new bundle.
         */
        val verifyBundle = project.tasks.register(VERIFY_BUNDLE_TASK_NAME, VerifyBundle::class.java) { verify ->
            verify.bundle.set(jarTask.flatMap(Jar::getArchiveFile))
            verify.setDependenciesFrom(calculatorTask)
        }
        jarTask.configure { jar ->
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
            task.destinationDirectory.set(jarTask.flatMap(Jar::getDestinationDirectory))
            task.archiveBaseName.set(jarTask.flatMap(Jar::getArchiveBaseName))
            task.archiveAppendix.set(jarTask.flatMap(Jar::getArchiveAppendix))

            // Configure the CPK archive contents.
            task.setLibrariesFrom(constraintsTask)
            task.cordapp.set(jarTask.flatMap(Jar::getArchiveFile))
            task.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    t.sign(cordapp.signing, (t as PackagingTask).archiveFile.get().asFile)
                }
            }
        }
        project.artifacts.add(ARCHIVES_CONFIGURATION, cpkTask)
        project.artifacts.add(CORDA_CPK_CONFIGURATION_NAME, cpkTask)
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
