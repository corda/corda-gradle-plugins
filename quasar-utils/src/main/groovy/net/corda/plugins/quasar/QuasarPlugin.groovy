package net.corda.plugins.quasar

import groovy.transform.CompileStatic
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
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion
import java.util.stream.StreamSupport
import javax.inject.Inject

import static java.util.stream.Collectors.toList
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
@CompileStatic
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = 'quasar'
    private static final String MINIMUM_GRADLE_VERSION = '7.0'
    private static final String CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = 'cordaRuntimeOnly'
    @PackageScope static final String defaultGroup = "co.paralleluniverse"
    @PackageScope static final String defaultVersion = "0.7.15_r3"
    @PackageScope static final String defaultClassifier = ""

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
        def quasarGroup = rootProject.findProperty('quasar_group')?.toString()?.trim() ?: defaultGroup
        def quasarVersion = rootProject.findProperty('quasar_version')?.toString()?.trim() ?: defaultVersion
        def quasarClassifier = rootProject.findProperty('quasar_classifier')?.toString()?.trim() ?: defaultClassifier
        def quasarPackageExclusions = rootProject.findProperty('quasar_exclusions') ?: Collections.emptyList()
        if (!(quasarPackageExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_exclusions property must be an Iterable<String>")
        }
        def quasarClassLoaderExclusions = rootProject.findProperty('quasar_classloader_exclusions') ?: Collections.emptyList()
        if (!(quasarClassLoaderExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_classloader_exclusions property must be an Iterable<String>")
        }
        def quasarExtension = project.extensions.create(QUASAR, QuasarExtension, objects,
            quasarGroup, quasarVersion, quasarClassifier,
            toStrings(quasarPackageExclusions as Iterable<?>),
            toStrings(quasarClassLoaderExclusions as Iterable<?>)
        )

        QuasarAdapter quasarAdapter = new QuasarAdapter(quasarExtension, project.dependencies, project.logger)
        addQuasarDependencies(project, quasarAdapter)
        configureQuasarTasks(project, quasarAdapter)
    }

    private void addQuasarDependencies(Project project, QuasarAdapter adapter) {
        def configurations = project.configurations
        def quasar = configurations.create(QUASAR)
            .withDependencies(new QuasarAction(adapter, false))
            .setVisible(false)

        // This definition of cordaRuntimeOnly must be consistent with the one in the cordapp plugin.
        def cordaRuntimeOnly = createBasicConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME, configurations)
            // Instrumented code needs both the Quasar bundle and its transitive dependencies at runtime.
            .withDependencies(new QuasarAction(adapter, true))
            .setTransitive(false)

        // If we're building a JAR then also add the Quasar bundle to the appropriate Java configurations.
        // This is also consistent with the cordapp plugin, which applies the 'java' plugin too.
        project.pluginManager.withPlugin('java') {
            // Add Quasar to the compile classpath WITHOUT any of its transitive dependencies.
            configurations[COMPILE_ONLY_CONFIGURATION_NAME].extendsFrom(quasar)

            // Adds the Quasar bundle to the runtime classpath WITH its transitive dependencies.
            configurations[RUNTIME_ONLY_CONFIGURATION_NAME].extendsFrom(cordaRuntimeOnly)
        }
    }

    private void configureQuasarTasks(Project project, QuasarAdapter adapter) {
        def quasarAgent = project.configurations[QUASAR]
        project.tasks.withType(Test).configureEach { Test task ->
            task.doFirst { Test t ->
                if (adapter.instrumentingTests) {
                    t.jvmArgs "-javaagent:${quasarAgent.singleFile}${adapter.options}",
                            "-Dco.paralleluniverse.fibers.verifyInstrumentation"
                }
            }
        }
        project.tasks.withType(JavaExec).configureEach { JavaExec task ->
            task.doFirst { JavaExec je ->
                if (adapter.instrumentingJavaExec) {
                    je.jvmArgs "-javaagent:${quasarAgent.singleFile}${adapter.options}",
                            "-Dco.paralleluniverse.fibers.verifyInstrumentation"
                }
            }
        }
    }

    private static Configuration createBasicConfiguration(String name, ConfigurationContainer configurations) {
        Configuration configuration = configurations.maybeCreate(name)
            .setVisible(false)
        configuration.canBeConsumed = false
        configuration.canBeResolved = false
        return configuration
    }

    private static List<String> toStrings(Iterable<?> items) {
        return StreamSupport.stream(items.spliterator(), false)
            .map { it?.toString()?.trim() }
            .collect(toList())
    }

    /**
     * Define operations to perform using {@link QuasarExtension}
     * once Gradle's configuration phase is complete.
     */
    @CompileStatic
    private static final class QuasarAdapter {
        private final QuasarExtension quasar
        private final DependencyHandler dependencies
        private final Logger logger

        @PackageScope
        QuasarAdapter(QuasarExtension quasar, DependencyHandler dependencies, Logger logger) {
            this.quasar = quasar
            this.dependencies = dependencies
            this.logger = logger
        }

        String getOptions() {
            quasar.options.get()
        }

        Dependency createDependency(Closure closure) {
            dependencies.create(quasar.dependency.get(), closure)
        }

        boolean isInstrumentingTests() {
            quasar.instrumentTests.get()
        }

        boolean isInstrumentingJavaExec() {
            quasar.instrumentJavaExec.get()
        }
    }

    @CompileStatic
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
            def quasarDependency = adapter.createDependency { ModuleDependency dep ->
                dep.transitive = isTransitive
            }
            dependencies.add(quasarDependency)
        }
    }
}
