package net.corda.plugins.quasar

import groovy.transform.PackageScope
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CONFIGURATION_NAME
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = "quasar"
    @PackageScope static final String defaultGroup = "co.paralleluniverse"
    @PackageScope static final String defaultVersion = "0.7.13_r3"
    @PackageScope static final String defaultClassifier = ""

    private final ObjectFactory objects

    @Inject
    QuasarPlugin(ObjectFactory objects) {
        this.objects = objects
    }

    @Override
    void apply(Project project) {
        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "compile", "compileOnly" and "runtime" configurations.
        project.pluginManager.apply(JavaPlugin)

        def rootProject = project.rootProject
        def quasarGroup = rootProject.hasProperty('quasar_group') ? rootProject.property('quasar_group') : defaultGroup
        def quasarVersion = rootProject.hasProperty('quasar_version') ? rootProject.property('quasar_version') : defaultVersion
        def quasarClassifier = rootProject.hasProperty('quasar_classifier') ? rootProject.property('quasar_classifier') : defaultClassifier
        def quasarPackageExclusions = rootProject.hasProperty("quasar_exclusions") ? rootProject.property('quasar_exclusions') : Collections.emptyList()
        if (!(quasarPackageExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_exclusions property must be an Iterable<String>")
        }
        def quasarClassLoaderExclusions = rootProject.hasProperty("quasar_classloader_exclusions") ? rootProject.property('quasar_classloader_exclusions') : Collections.emptyList()
        if (!(quasarClassLoaderExclusions instanceof Iterable<?>)) {
            throw new InvalidUserDataException("quasar_classloader_exclusions property must be an Iterable<String>")
        }
        def quasarExtension = project.extensions.create(QUASAR, QuasarExtension, objects,
                quasarGroup, quasarVersion, quasarClassifier, quasarPackageExclusions, quasarClassLoaderExclusions)

        addQuasarDependencies(project, quasarExtension)
        configureQuasarTasks(project, quasarExtension)
    }

    private void addQuasarDependencies(Project project, QuasarExtension extension) {
        def quasar = project.configurations.create(QUASAR)
        quasar.withDependencies { dependencies ->
            def quasarDependency = project.dependencies.create(extension.dependency.get()) {
                it.transitive = false
            }
            dependencies.add(quasarDependency)
        }

        // Add Quasar to the compile classpath WITHOUT any of its transitive dependencies.
        project.dependencies.add(COMPILE_ONLY_CONFIGURATION_NAME, quasar)

        // Instrumented code needs both the Quasar agent and its transitive dependencies at runtime.
        def cordaRuntime = createRuntimeConfiguration("cordaRuntime", project.configurations)
        cordaRuntime.withDependencies { dependencies ->
            def quasarDependency = project.dependencies.create(extension.dependency.get()) {
                it.transitive = true
            }
            dependencies.add(quasarDependency)
        }
    }

    private void configureQuasarTasks(Project project, QuasarExtension extension) {
        project.tasks.withType(Test).configureEach {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
        project.tasks.withType(JavaExec).configureEach {
            doFirst {
                jvmArgs "-javaagent:${project.configurations[QUASAR].singleFile}${extension.options.get()}",
                        "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    private static Configuration createRuntimeConfiguration(String name, ConfigurationContainer configurations) {
        Configuration configuration = configurations.findByName(name)
        if (configuration == null) {
            Configuration parent = configurations.getByName(RUNTIME_CONFIGURATION_NAME)
            configuration = configurations.create(name)
            configuration.transitive = false
            parent.extendsFrom(configuration)
        }
        return configuration
    }
}
