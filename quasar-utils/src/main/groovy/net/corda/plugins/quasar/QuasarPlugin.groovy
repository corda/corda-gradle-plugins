package net.corda.plugins.quasar

import groovy.transform.PackageScope
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ReadableResource
import org.gradle.api.resources.ResourceException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion

import javax.inject.Inject

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CONFIGURATION_NAME

class ExtractJavaPluginTask extends DefaultTask {

    final Provider<Boolean> javacPluginEnabled

    @Input
    final File thisPluginJar

    final File outputDir

    @Inject
    ExtractJavaPluginTask(JavacPluginExtension javacPluginExtension) {
        javacPluginEnabled = javacPluginExtension.enable
        String resourceName = QuasarPlugin.class.name.replace('.', '/') + '.class'
        String url = QuasarPlugin.class.classLoader.getResource(resourceName).toString()
        String prefix = "file:"
        int start = url.indexOf(prefix) + prefix.length()
        int end = url.indexOf('!', start)
        thisPluginJar = new File(url.substring(start, end))

        outputDir = new File(project.buildDir, "classes/javacPlugin")
        outputs.dir(outputDir)
    }

    @TaskAction
    def run() {
        if(javacPluginEnabled) {
            if (!outputDir.exists()) outputDir.mkdirs()
            project.resources
            ReadableResource readableResource = new ReadableResource() {
                private URL url = QuasarPlugin.class.getResource("/META-INF/javac-plugin.tar")
                @Override
                InputStream read() throws MissingResourceException, ResourceException {
                    return url.openStream()
                }

                @Override
                String getDisplayName() {
                    return "javac-plugin.tar"
                }

                @Override
                URI getURI() {
                    return url.toURI()
                }

                @Override
                String getBaseName() {
                    return "javac-plugin"
                }
            }

            project.copy {
                from project.tarTree(readableResource)
                into outputDir
            }
        }
    }
}

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    private static final String QUASAR = "quasar"
    private static final String MINIMUM_GRADLE_VERSION = "5.1"
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

        TaskProvider<ExtractJavaPluginTask> extractJavacPluginTask = project.tasks.register("extractJavacPlugin", ExtractJavaPluginTask, extension.javacPluginExtension)

        project.tasks.withType(JavaCompile).configureEach {
            inputs.files(extractJavacPluginTask.get().outputs.files)
            doFirst {
                List<String> compilerArgs = ["-Xplugin:net.corda.plugins.javac.quasar.SuspendableChecker"]
                extension.javacPluginExtension.suspendableAnnotationMarkers.get().with { classNames ->
                    if(classNames) {
                        compilerArgs += "annotations:${classNames.join(',')}"
                    }
                }
                extension.javacPluginExtension.suspendableThrowableMarkers.get().with { classNames ->
                    if(classNames) {
                        compilerArgs += "throwables:${classNames.join(',')}"
                    }
                }
                options.annotationProcessorPath += extractJavacPluginTask.get().outputs.files
                options.compilerArgs += compilerArgs.join(" ")
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
