package net.corda.plugins.cpk

import aQute.bnd.gradle.BndBuilderPlugin
import aQute.bnd.gradle.BundleTaskConvention
import net.corda.plugins.cpk.SignJar.Companion.sign
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.file.ProjectLayout
import org.gradle.api.java.archives.Attributes
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.Property
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
        private const val CORDAPP_EXTENSION_NAME = "cordapp"
        private const val OSGI_EXTENSION_NAME = "osgi"
        private const val MIN_GRADLE_VERSION = "6.6"
        private const val CPK_TASK_NAME = "cpk"
        private const val UNKNOWN = "Unknown"
        private const val VERSION_X = 999

        val Array<String>.packageRange: IntRange get() {
            val firstIdx = if (size > 2 && this[0] == "META-INF" && this[1] == "versions") { 3 } else { 0 }
            return firstIdx..size - 2
        }

        private fun getBndVersion(): String {
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

        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "implementation", "compileOnly" and "runtimeOnly" configurations.
        project.pluginManager.apply(JavaPlugin::class.java)

        // Apply the Bnd "builder" plugin to generate OSGi metadata for the CorDapp.
        project.pluginManager.apply(BndBuilderPlugin::class.java)

        // Create our plugin's "cordapp" extension.
        cordapp = project.extensions.create(CORDAPP_EXTENSION_NAME, CordappExtension::class.java, getBndVersion())

        project.configurations.apply {
            createCompileOnlyConfiguration(CORDAPP_CONFIGURATION_NAME)
            createCompileOnlyConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME)
            createImplementationConfiguration(CORDA_EMBEDDED_CONFIGURATION_NAME)
            createRuntimeOnlyConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME)
            findByName(CORDA_CPK_CONFIGURATION_NAME) ?: create(CORDA_CPK_CONFIGURATION_NAME)

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).withDependencies { dependencies ->
                val bndDependency = project.dependencies.create("biz.aQute.bnd:biz.aQute.bnd.annotation:" + cordapp.bndVersion.get())
                dependencies.add(bndDependency)
            }
        }

        configureCordappTasks(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR. Ensure that the
     * JAR is reproducible, i.e. that its SHA-256 hash is stable!
     */
    private fun configureCordappTasks(project: Project) {
        /**
         * Generate an extra resource file containing constraints for all of this CorDapp's dependencies.
         */
        val constraintsDir = layouts.buildDirectory.dir("generated-constraints")
        val constraintsTask = project.tasks.register(DEPENDENCY_CONSTRAINTS_TASK_NAME, DependencyConstraintsTask::class.java) { task ->
            task.dependsOnConstraints()
            task.constraintsDir.set(constraintsDir)
        }
        val sourceSets = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets
        sourceSets.getByName("main") { main ->
            main.output.dir(mapOf("builtBy" to constraintsTask), constraintsDir)
        }

        val jarTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java) { task ->
            val osgi = task.extensions.create(OSGI_EXTENSION_NAME, OsgiExtension::class.java, project, task)
            osgi.embed(constraintsTask.flatMap(DependencyConstraintsTask::embeddedJars))

            val noPackages = project.objects.setProperty(String::class.java)
                .apply(HasConfigurableValue::disallowChanges)
            val autoPackages = project.objects.setProperty(String::class.java)
            osgi.exportAll(osgi.autoExport.flatMap { isAuto ->
                if (isAuto) {
                    autoPackages
                } else {
                    noPackages
                }
            })

            // Install a "listener" for files copied into the CorDapp jar.
            // We will extract the names of the non-empty packages inside
            // this jar as we go...
            task.rootSpec.eachFile { file ->
                if (!file.isDirectory) {
                    val elements = file.relativePath.segments
                    val packageRange = elements.packageRange
                    if (!packageRange.isEmpty()) {
                        val packageName = elements.slice(packageRange)
                        if (packageName[0] != "META-INF" && packageName[0] != "OSGI-INF") {
                            autoPackages.add(packageName.joinToString("."))
                        }
                    }
                }
            }

            with(task.convention.getPlugin(BundleTaskConvention::class.java)) {
                // Add a Bnd instruction to export the set of observed package names.
                bnd(osgi.exports)

                // Add Bnd instructions to embed requested jars into this bundle.
                bnd(osgi.embeddedJars)
            }

            task.doFirst { t ->
                t as Jar
                t.fileMode = Integer.parseInt("444", 8)
                t.dirMode = Integer.parseInt("555", 8)
                t.manifestContentCharset = "UTF-8"
                t.isPreserveFileTimestamps = false
                t.isReproducibleFileOrder = true
                t.entryCompression = DEFLATED
                t.includeEmptyDirs = false
                t.isCaseSensitive = true
                t.isZip64 = true

                val attributes = t.manifest.attributes
                // check whether metadata has been configured (not mandatory for non-flow, non-contract gradle build files)
                if (cordapp.contract.isEmpty() && cordapp.workflow.isEmpty()) {
                    throw InvalidUserDataException("Cordapp metadata not defined for this gradle build file. See https://docs.corda.net/head/cordapp-build-systems.html#separation-of-cordapp-contracts-flows-and-services")
                }

                configureCordappAttributes(osgi.symbolicName.get(), attributes)
            }.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    sign(t, cordapp.signing, (t as Jar).archiveFile.get().asFile)
                } else {
                    t.logger.lifecycle("CorDapp JAR signing is disabled, the CorDapp's contracts will not use signature constraints.")
                }
            }
        }

        /**
         * Package the CorDapp and all of its dependencies into a Jar archive with CPK extension.
         * The CPK artifact should have the same base-name and appendix as the CorDapp's [Jar] task.
         */
        val cpkTask = project.tasks.register(CPK_TASK_NAME, PackagingTask::class.java) { task ->
            // Basic configuration of the packaging task.
            task.destinationDirectory.set(jarTask.flatMap(Jar::getDestinationDirectory))
            task.archiveBaseName.set(jarTask.flatMap(Jar::getArchiveBaseName))
            task.archiveAppendix.set(jarTask.flatMap(Jar::getArchiveAppendix))

            // Configure the CPK archive contents.
            task.setDependenciesFrom(constraintsTask)
            task.cordapp.set(jarTask.flatMap(Jar::getArchiveFile))
            task.doLast { t ->
                if (cordapp.signing.enabled.get()) {
                    sign(t, cordapp.signing, (t as PackagingTask).archiveFile.get().asFile)
                }
            }
        }
        project.artifacts.add(ARCHIVES_CONFIGURATION, cpkTask)
        project.artifacts.add(CORDA_CPK_CONFIGURATION_NAME, cpkTask)
    }

    private fun configureCordappAttributes(symbolicName: String, attributes: Attributes) {
        attributes[BUNDLE_SYMBOLICNAME] = symbolicName

        if (!cordapp.contract.isEmpty()) {
            val contractName = cordapp.contract.name.getOrElse(symbolicName)
            val vendor = cordapp.contract.vendor.getOrElse(UNKNOWN)
            val licence = cordapp.contract.licence.getOrElse(UNKNOWN)
            attributes[BUNDLE_NAME] = contractName
            attributes[BUNDLE_VENDOR] = vendor
            attributes[BUNDLE_LICENSE] = licence
            attributes["Cordapp-Contract-Name"] = contractName
            attributes["Cordapp-Contract-Version"] = checkCorDappVersionId(cordapp.contract.versionId)
            attributes["Cordapp-Contract-Vendor"] = vendor
            attributes["Cordapp-Contract-Licence"] = licence
        }
        if (!cordapp.workflow.isEmpty()) {
            val workflowName = cordapp.contract.name.getOrElse(symbolicName)
            val vendor = cordapp.workflow.vendor.getOrElse(UNKNOWN)
            val licence = cordapp.workflow.licence.getOrElse(UNKNOWN)
            attributes[BUNDLE_NAME] = workflowName
            attributes[BUNDLE_VENDOR] = vendor
            attributes[BUNDLE_LICENSE] = licence
            attributes["Cordapp-Workflow-Name"] = workflowName
            attributes["Cordapp-Workflow-Version"] = checkCorDappVersionId(cordapp.workflow.versionId)
            attributes["Cordapp-Workflow-Vendor"] = vendor
            attributes["Cordapp-Workflow-Licence"] = licence
        }

        val (targetPlatformVersion, minimumPlatformVersion) = checkPlatformVersionInfo()
        attributes["Target-Platform-Version"] = targetPlatformVersion
        attributes["Min-Platform-Version"] = minimumPlatformVersion
        if (cordapp.sealing.enabled.get()) {
            attributes["Sealed"] = true
        }
    }

    private fun checkPlatformVersionInfo(): Pair<Int, Int> {
        // If the minimum platform version is not set, default to X.
        val minimumPlatformVersion: Int = cordapp.minimumPlatformVersion.getOrElse(VERSION_X)
        val targetPlatformVersion = cordapp.targetPlatformVersion.orNull
                ?: throw InvalidUserDataException("CorDapp `targetPlatformVersion` was not specified in the `cordapp` metadata section.")
        if (targetPlatformVersion < VERSION_X) {
            throw InvalidUserDataException("CorDapp `targetPlatformVersion` must not be smaller than $VERSION_X.")
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
}
