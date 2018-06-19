package net.corda.plugins

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.assertj.core.api.Assertions.*
import static org.gradle.testkit.runner.TaskOutcome.*
import static org.junit.Assert.*

class QuasarPluginTest {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", ".")

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()

    @Test
    void checkDefaultVersionIsRuntime() {
        def quasarVersion = QuasarPlugin.defaultVersion

        def buildFile = testProjectDir.newFile("build.gradle")
        buildFile.text = """
plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to runtime configurations'
    
repositories {
    mavenLocal()
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

configurations.runtime.forEach {
    println "runtime: \${it.name}"
}

configurations.cordaRuntime.forEach {
    println "cordaRuntime: \${it.name}"
}
"""
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("--info", "build", "-g", TEST_GRADLE_USER_HOME)
            .withPluginClasspath()
            .build()
        println result.output

        def output = result.output.readLines()
        assertThat(output).containsOnlyOnce(
            "runtime: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "cordaRuntime: quasar-core-$quasarVersion-jdk8.jar".toString()
        )

        def build = result.task(":build")
        assertNotNull(build)
        assertEquals(UP_TO_DATE, build.outcome)
    }

    @Test
    void checkOverriddenVersionIsRuntime() {
        def quasarVersion = "0.7.9"
        assertNotEquals(quasarVersion, QuasarPlugin.defaultVersion)

        def buildFile = testProjectDir.newFile("build.gradle")
        buildFile.text = """
buildscript {
    ext {
        quasar_group = 'co.paralleluniverse'
        quasar_version = '$quasarVersion'
    }
}

plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to runtime configurations'
    
repositories {
    mavenLocal()
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

configurations.runtime.forEach {
    println "runtime: \${it.name}"
}

configurations.cordaRuntime.forEach {
    println "cordaRuntime: \${it.name}"
}
"""
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", "build", "-g", TEST_GRADLE_USER_HOME)
                .withPluginClasspath()
                .build()
        println result.output

        def output = result.output.readLines()
        assertThat(output).containsOnlyOnce(
                "runtime: quasar-core-$quasarVersion-jdk8.jar".toString(),
                "cordaRuntime: quasar-core-$quasarVersion-jdk8.jar".toString()
        )

        def build = result.task(":build")
        assertNotNull(build)
        assertEquals(UP_TO_DATE, build.outcome)
    }
}