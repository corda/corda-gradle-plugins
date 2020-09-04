package net.corda.plugins.quasar

import groovy.transform.PackageScope
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GradleVersion
import org.gradle.api.tasks.compile.JavaCompile

import java.util.jar.Attributes
import java.util.jar.Manifest

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
    private static final String MINIMUM_GRADLE_VERSION = "5.1"
    private static final String JAVAC_PLUGIN = "javac_plugin"
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
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw new GradleException("The Quasar-Utils plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }

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
                quasarGroup,
                quasarVersion,
                quasarClassifier,
                quasarPackageExclusions,
                quasarClassLoaderExclusions
        )

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
        project.configurations.getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(quasar)

        // Instrumented code needs both the Quasar agent and its transitive dependencies at runtime.
        def cordaRuntime = createRuntimeConfiguration("cordaRuntime", project.configurations)
        cordaRuntime.withDependencies { dependencies ->
            def quasarDependency = project.dependencies.create(extension.dependency.get()) {
                it.transitive = true
            }
            dependencies.add(quasarDependency)
        }

        def javacPluginConfiguration = project.configurations.create(JAVAC_PLUGIN)
        javacPluginConfiguration.withDependencies { dependencies ->
            if(extension.enableJavacPlugin.get()) {
                def cls = getClass()
                def resourceName = cls.name.replace('.', '/') + ".class"
                def classUrl = cls.classLoader.getResource(resourceName)
                if (classUrl.protocol.startsWith("jar")) {
                    def path = classUrl.toString()
                    String manifestPath = path.substring(0, path.lastIndexOf("!") + 1) +
                            "/META-INF/MANIFEST.MF"
                    Manifest manifest = new Manifest(new URL(manifestPath).openStream())
                    Attributes attr = manifest.getMainAttributes()
                    String version = attr.getValue("version")
                    dependencies.add(project.dependencies.create("net.corda.plugins:javac-plugin:$version") {
                        it.transitive = false
                    })
                }
            }
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
        project.tasks.withType(JavaCompile).configureEach {
            doFirst {
                if(extension.enableJavacPlugin.get()) {
                    options.annotationProcessorPath += project.configurations[JAVAC_PLUGIN]
                    options.compilerArgs += "-Xplugin:net.corda.plugins.javac.quasar.SuspendableChecker"
                }
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
