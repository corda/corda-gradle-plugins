package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    static final defaultGroup = "co.paralleluniverse"
    // JDK 11 official support in 0.8.0 (https://github.com/puniverse/quasar/issues/317)
    static final defaultVersion = "0.8.0"

    @Override
    void apply(Project project) {
        // Apply the Java plugin on the assumption that we're building a JAR.
        // This will also create the "compile", "compileOnly" and "runtime" configurations.
        project.pluginManager.apply(JavaPlugin)

        Utils.createRuntimeConfiguration("cordaRuntime", project.configurations)
        def quasar = project.configurations.create("quasar")

        def rootProject = project.rootProject
        def quasarGroup = rootProject.hasProperty("quasar_group") ? rootProject.ext.quasar_group : defaultGroup
        def quasarVersion = rootProject.hasProperty("quasar_version") ? rootProject.ext.quasar_version : defaultVersion
        def quasarDependency = "${quasarGroup}:quasar-core:${quasarVersion}"
        project.dependencies.add("quasar", quasarDependency) {
            it.transitive = false
        }
        project.dependencies.add("cordaRuntime", quasarDependency) {
            // Ensure that Quasar's transitive dependencies are available at runtime (only).
            it.transitive = false
        }
        // This adds Quasar to the compile classpath WITHOUT any of its transitive dependencies.
        project.dependencies.add("compileOnly", quasar) {
            it.transitive = false
        }

        project.tasks.withType(Test) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
        project.tasks.withType(JavaExec) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }
}
