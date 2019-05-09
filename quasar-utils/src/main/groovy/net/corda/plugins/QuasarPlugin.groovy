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
    static final defaultVersion = "0.7.10"

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
        def quasarDependency = "${quasarGroup}:quasar-core:${quasarVersion}:jdk8@jar"
        project.dependencies.add("quasar", quasarDependency)
        project.dependencies.add("cordaRuntime", quasarDependency) {
            // Ensure that Quasar's transitive dependencies are available at runtime (only).
            it.transitive = true
        }
        // This adds Quasar to the compile classpath WITHOUT any of its transitive dependencies.
        project.dependencies.add("compileOnly", quasar)

        project.tasks.withType(Test) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}=x(antlr**;bftsmart**;co.paralleluniverse**;com.codahale**;com.esotericsoftware**;com.fasterxml**;com.google**;com.ibm**;com.intellij**;com.jcabi**;com.nhaarman**;com.opengamma**;com.typesafe**;com.zaxxer**;de.javakaffee**;groovy**;groovyjarjarantlr**;groovyjarjarasm**;io.atomix**;io.github**;io.netty**;jdk**;junit**;kotlin**;net.bytebuddy**;net.i2p**;org.apache**;org.assertj**;org.bouncycastle**;org.codehaus**;org.crsh**;org.dom4j**;org.fusesource**;org.h2**;org.hamcrest**;org.hibernate**;org.jboss**;org.jcp**;org.joda**;org.junit**;org.mockito**;org.objectweb**;org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**;org.jolokia**)"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
        project.tasks.withType(JavaExec) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}=x(antlr**;bftsmart**;co.paralleluniverse**;com.codahale**;com.esotericsoftware**;com.fasterxml**;com.google**;com.ibm**;com.intellij**;com.jcabi**;com.nhaarman**;com.opengamma**;com.typesafe**;com.zaxxer**;de.javakaffee**;groovy**;groovyjarjarantlr**;groovyjarjarasm**;io.atomix**;io.github**;io.netty**;jdk**;junit**;kotlin**;net.bytebuddy**;net.i2p**;org.apache**;org.assertj**;org.bouncycastle**;org.codehaus**;org.crsh**;org.dom4j**;org.fusesource**;org.h2**;org.hamcrest**;org.hibernate**;org.jboss**;org.jcp**;org.joda**;org.junit**;org.mockito**;org.objectweb**;org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**;org.jolokia**)"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }
}
