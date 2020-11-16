package net.corda.plugins.quasar

import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.util.GradleVersion

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * QuasarPlugin creates "quasar" and "quasarAgent" configurations and adds Quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = "quasar"
    private static final String QUASAR_AGENT = "quasarAgent"
    private static final String MINIMUM_GRADLE_VERSION = "5.1"
    private static final String CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
    private static final String CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
    @PackageScope static final String defaultGroup = "co.paralleluniverse"
    @PackageScope static final String defaultVersion = "0.8.4_r3"

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

        addQuasarDependencies(project, quasarExtension)
        configureQuasarTasks(project, quasarExtension)
    }

    private void addQuasarDependencies(Project project, QuasarExtension extension) {
        def quasar = project.configurations.create(QUASAR)
        quasar.withDependencies { dependencies ->
            def quasarDependency = project.dependencies.create(extension.dependency.get()) { dep ->
                dep.transitive = false
            }
            dependencies.add(quasarDependency)
        }

        def quasarAgent = project.configurations.create(QUASAR_AGENT)
        quasarAgent.withDependencies { dependencies ->
            def quasarAgentDependency = project.dependencies.create(extension.agent.get()) { dep ->
                dep.transitive = false
            }
            dependencies.add(quasarAgentDependency)
        }

        // If we're building a JAR then also add the Quasar bundle to the appropriate configurations.
        project.pluginManager.withPlugin('java') {
            // Add Quasar bundle to the compile classpath WITHOUT any of its transitive dependencies.
            def cordaProvided = createCompileOnlyConfiguration(CORDA_PROVIDED_CONFIGURATION_NAME, project.configurations)
            cordaProvided.withDependencies(new QuasarAction(project.dependencies, extension, false))

            // Instrumented code needs both the Quasar bundle and its transitive dependencies at runtime.
            def cordaRuntimeOnly = createRuntimeOnlyConfiguration(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME, project.configurations)
            cordaRuntimeOnly.withDependencies(new QuasarAction(project.dependencies, extension, true))
        }
    }

    private void configureQuasarTasks(Project project, QuasarExtension extension) {
        project.tasks.withType(Test).configureEach {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR_AGENT].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
        project.tasks.withType(JavaExec).configureEach {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR_AGENT].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
    }

    private static Configuration createRuntimeOnlyConfiguration(String name, ConfigurationContainer configurations) {
        Configuration configuration = configurations.findByName(name)
        if (configuration == null) {
            configuration = configurations.create(name)
            configuration.transitive = false
            configurations.getByName(RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        }
        return configuration
    }

    private static Configuration createCompileOnlyConfiguration(String name, ConfigurationContainer configurations) {
        Configuration configuration = configurations.findByName(name)
        if (configuration == null) {
            configuration = configurations.create(name)
            configurations.getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
            configurations.matching { it.name.endsWith("CompileOnly") }.configureEach { cfg ->
                cfg.extendsFrom(configuration)
            }
        }
        return configuration
    }

    private static class QuasarAction implements Action<DependencySet> {
        private final DependencyHandler handler
        private final QuasarExtension extension
        private final boolean isTransitive

        QuasarAction(DependencyHandler handler, QuasarExtension extension, boolean isTransitive) {
            this.handler = handler
            this.extension = extension
            this.isTransitive = isTransitive
        }

        @Override
        void execute(DependencySet dependencies) {
            if (isEmpty(extension.suspendableAnnotation)) {
                def quasarDependency = handler.create(extension.dependency.get()) { dep ->
                    dep.transitive = isTransitive
                }
                dependencies.add(quasarDependency)
            }
        }

        private static boolean isEmpty(Property<String> property) {
            !property.isPresent() || property.get().isBlank()
        }
    }
}
