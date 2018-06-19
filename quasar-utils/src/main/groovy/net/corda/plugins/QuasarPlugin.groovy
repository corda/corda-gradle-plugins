package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration and adds quasar as a dependency.
 */
class QuasarPlugin implements Plugin<Project> {

    static defaultGroup = "co.paralleluniverse"
    static defaultVersion = "0.7.10"

    @Override
    void apply(Project project) {
        Utils.createRuntimeConfiguration("cordaRuntime", project)

        project.configurations.create("quasar")

        def quasarGroup = project.rootProject.hasProperty("quasar_group") ? project.rootProject.ext.quasar_group : defaultGroup
        def quasarVersion = project.rootProject.hasProperty("quasar_version") ? project.rootProject.ext.quasar_version : defaultVersion
//        To add a local .jar dependency:
//        project.dependencies.add("quasar", project.files("${project.rootProject.projectDir}/lib/quasar.jar"))
        project.dependencies.add("quasar", "${quasarGroup}:quasar-core:${quasarVersion}:jdk8@jar")
        project.dependencies.add("cordaRuntime", project.configurations.getByName("quasar"))

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
