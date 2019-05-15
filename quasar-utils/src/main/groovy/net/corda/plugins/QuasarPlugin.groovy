package net.corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    static final defaultGroup = "co.paralleluniverse"
    static final defaultVersion = "0.7.10"

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

        def quasarExtension = project.extensions.create("quasar", QuasarExtension, objects)

        Utils.createRuntimeConfiguration("cordaRuntime", project.configurations)
        def quasar = project.configurations.create("quasar")

        def rootProject = project.rootProject
        def quasarGroup = rootProject.hasProperty('quasar_group') ? rootProject.property('quasar_group') : defaultGroup
        def quasarVersion = rootProject.hasProperty('quasar_version') ? rootProject.property('quasar_version') : defaultVersion
        def quasarDependency = "${quasarGroup}:quasar-core:${quasarVersion}:jdk8@jar"
        project.dependencies.add("quasar", quasarDependency)
        project.dependencies.add("cordaRuntime", quasarDependency) {
            // Ensure that Quasar's transitive dependencies are available at runtime (only).
            it.transitive = true
        }
        // This adds Quasar to the compile classpath WITHOUT any of its transitive dependencies.
        project.dependencies.add("compileOnly", quasar)

        project.tasks.withType(Test) {
            doFirst {
                jvmArgs "-javaagent:${project.configurations.quasar.singleFile}${quasarExtension.exclusions.get()}"
                jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
        project.tasks.withType(JavaExec) {
            doFirst {
                jvmArgs "-javaagent:${project.configurations.quasar.singleFile}${quasarExtension.exclusions.get()}"
                jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
            }
        }
    }
}
