package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.configurations.create("quasar")
//        To add a local .jar dependency:

        // If a quasar group has been specified then use it, otherwise default to the included library
        try {
            project.dependencies.add("quasar", "${project.rootProject.ext.quasar_group}:quasar-core:${project.rootProject.ext.quasar_version}:jdk8@jar")
        } catch (MissingPropertyException e) {
            project.dependencies.add("quasar", project.files("${project.rootProject.projectDir}/lib/quasar.jar"))
        }

        project.dependencies.add("runtime", project.configurations.getByName("quasar"))

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
