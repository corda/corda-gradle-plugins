package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    static final defaultGroup = "co.paralleluniverse"
    static final defaultVersion = "0.7.10"

    @Override
    void apply(Project project) {
        Utils.createRuntimeConfiguration("cordaRuntime", project)
        project.configurations.create("quasar")

        def rootProject = project.rootProject
        def quasarGroup = rootProject.hasProperty("quasar_group") ? rootProject.ext.quasar_group : defaultGroup
        def quasarVersion = rootProject.hasProperty("quasar_version") ? rootProject.ext.quasar_version : defaultVersion
        def quasarDependency = "${quasarGroup}:quasar-core:${quasarVersion}:jdk8@jar"
        project.dependencies.add("quasar", quasarDependency)
        project.dependencies.add("cordaRuntime", quasarDependency) {
            // Ensure that Quasar's transitive dependencies are available at runtime (only).
            it.transitive = true
        }

        project.tasks.withType(Test).all {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
        project.tasks.withType(JavaExec).all {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }
}
