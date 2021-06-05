package net.corda.plugins.quasar

import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME

/**
 * QuasarPlugin creates "quasar" and "quasarAgent" configurations and adds Quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = 'quasar'
    private static final String QUASAR_AGENT = 'quasarAgent'
    private static final String MINIMUM_GRADLE_VERSION = '6.6'
    private static final String CORDA_PROVIDED_CONFIGURATION_NAME = 'cordaProvided'
    private static final String CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = 'cordaRuntimeOnly'
    @PackageScope static final String QUASAR_ARTIFACT_NAME = 'quasar-core-osgi'
    @PackageScope static final String defaultGroup = 'co.paralleluniverse'
    @PackageScope static final String defaultVersion = '0.8.6_r3'

    private final ObjectFactory objects

    @Inject
    QuasarPlugin(ObjectFactory objects) {
        this.objects = objects
    }

    @Override
    void apply(Project project) {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw new GradleException("The Quasar-Utils plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }

        def rootProject = project.rootProject
        def quasarGroup = rootProject.findProperty('quasar_group')?.toString() ?: defaultGroup
        def quasarVersion = rootProject.findProperty('quasar_version')?.toString() ?: defaultVersion
        def quasarSuspendable = rootProject.findProperty('quasar_suspendable_annotation')?.toString()?.trim()
        def quasarPackageExclusions = rootProject.findProperty('quasar_exclusions') ?: Collections.emptyList()
        if (!(quasarPackageExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_exclusions property must be an Iterable<String>")
        }
        def quasarClassLoaderExclusions = rootProject.findProperty('quasar_classloader_exclusions') ?: Collections.emptyList()
        if (!(quasarClassLoaderExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_classloader_exclusions property must be an Iterable<String>")
        }
        def quasarExtension = project.extensions.create(QUASAR, QuasarExtension, objects,
            quasarGroup, quasarVersion, quasarSuspendable, quasarPackageExclusions, quasarClassLoaderExclusions)

        QuasarAdapter quasarAdapter = new QuasarAdapter(quasarExtension, project.dependencies, project.logger)
        addQuasarDependencies(project, quasarAdapter)
        configureQuasarTasks(project, quasarAdapter)
    }

    private void addQuasarDependencies(Project project, QuasarAdapter adapter) {
        def quasar = project.configurations.create(QUASAR)
        quasar.visible = false
        quasar.withDependencies { dependencies ->
            def quasarDependency = adapter.createDependency { dep ->
                dep.transitive = false
            }
            dependencies.add(quasarDependency)
        }

        def quasarAgent = project.configurations.create(QUASAR_AGENT)
        quasarAgent.visible = false
        quasarAgent.withDependencies { dependencies ->
            def quasarAgentDependency = adapter.createAgent { dep ->
                dep.transitive = false
            }
            dependencies.add(quasarAgentDependency)
        }

        // If we're building a JAR then also add the Quasar bundle to the appropriate configurations.
        project.pluginManager.withPlugin('java') {
            // Add Quasar bundle to the compile classpath WITHOUT any of its transitive dependencies.
            // This definition of cordaProvided must be kept aligned with the one in the corda-cpk plugin.
            def cordaProvided = createCompileConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME, project.configurations)
            cordaProvided.withDependencies(new QuasarAction(adapter, false))

            // Instrumented code needs both the Quasar bundle and its transitive dependencies at runtime.
            // The definition of cordaRuntimeOnly must be kept aligned with the one in the corda-cpk plugin.
            def cordaRuntimeOnly = createRuntimeOnlyConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME, project.configurations)
            cordaRuntimeOnly.withDependencies(new QuasarAction(adapter, true))
        }
    }

    private void configureQuasarTasks(Project project, QuasarAdapter adapter) {
        def quasarAgent = project.configurations[QUASAR_AGENT]
        project.tasks.withType(Test).configureEach {
            doFirst {
                if (adapter.withoutSuspendableAnnotation) {
                    adapter.warnNoSuspendableAnnotation()
                }
                jvmArgs "-javaagent:${quasarAgent.singleFile}${adapter.options}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
        project.tasks.withType(JavaExec).configureEach {
            doFirst {
                if (adapter.withoutSuspendableAnnotation) {
                    adapter.warnNoSuspendableAnnotation()
                }
                jvmArgs "-javaagent:${quasarAgent.singleFile}${adapter.options}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
    }

    private static Configuration createRuntimeOnlyConfiguration(String name, ConfigurationContainer configurations) {
        Configuration configuration = configurations.maybeCreate(name)
            .setTransitive(false)
            .setVisible(false)
        configuration.canBeConsumed = false
        configuration.canBeResolved = false
        configurations.getByName(RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        return configuration
    }

    private static Configuration createCompileConfiguration(String name, ConfigurationContainer configurations) {
        return createCompileConfiguration(name, "Implementation", configurations)
    }

    private static Configuration createCompileConfiguration(String name, String testSuffix, ConfigurationContainer configurations) {
        Configuration configuration = configurations.maybeCreate(name)
            .setVisible(false)
        configuration.canBeConsumed = false
        configuration.canBeResolved = false
        configurations.getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        configurations.matching { it.name.endsWith(testSuffix) }.configureEach { cfg ->
            cfg.extendsFrom(configuration)
        }
        return configuration
    }

    /**
     * Define operations to perform using {@link QuasarExtension}
     * once Gradle's configuration phase is complete.
     */
    private static final class QuasarAdapter {
        private final AtomicBoolean warnFlag
        private final QuasarExtension quasar
        private final DependencyHandler dependencies
        private final Logger logger

        @PackageScope
        QuasarAdapter(QuasarExtension quasar, DependencyHandler dependencies, Logger logger) {
            this.warnFlag = new AtomicBoolean()
            this.quasar = quasar
            this.dependencies = dependencies
            this.logger = logger
        }

        String getOptions() {
            quasar.options.get()
        }

        Dependency createAgent(Closure closure) {
            dependencies.create(quasar.agent.get(), closure)
        }

        Dependency createDependency(Closure closure) {
            dependencies.create(quasar.dependency.get(), closure)
        }

        boolean isWithoutSuspendableAnnotation() {
            isEmpty(quasar.suspendableAnnotation)
        }

        void warnNoSuspendableAnnotation() {
            if (!warnFlag.getAndSet(true)) {
                logger.warn("""\
WARNING: Quasar's @Suspendable annotation has not been configured!
Define Gradle property 'quasar_suspendable_annotation' to be the class name of the @Suspendable annotation your project uses.
Adding ${QUASAR_ARTIFACT_NAME} to the classpath, to make Quasar's built-in @Suspendable annotation available.""")
            }
        }

        private static boolean isEmpty(Property<String> property) {
            !property.isPresent() || property.get().isBlank()
        }
    }

    private static final class QuasarAction implements Action<DependencySet> {
        private final QuasarAdapter adapter
        private final boolean isTransitive

        @PackageScope
        QuasarAction(QuasarAdapter adapter, boolean isTransitive) {
            this.adapter = adapter
            this.isTransitive = isTransitive
        }

        @Override
        void execute(DependencySet dependencies) {
            if (adapter.withoutSuspendableAnnotation) {
                adapter.warnNoSuspendableAnnotation()
                def quasarDependency = adapter.createDependency { dep ->
                    dep.transitive = isTransitive
                }
                dependencies.add(quasarDependency)
            }
        }
    }
}
