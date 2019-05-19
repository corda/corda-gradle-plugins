package net.corda.plugins

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class QuasarPluginTest {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", ".")
    private static final String QUASAR_VERSION = QuasarPlugin.defaultVersion

    @TempDir
    public Path testProjectDir

    @BeforeEach
    void setup() {
        Utilities.installResource(testProjectDir, "settings.gradle")
        Utilities.installResource(testProjectDir, "repositories.gradle")
        Utilities.installResource(testProjectDir, "src/test/java/BasicTest.java")
    }

    @Test
    void checkDefaultVersionIsUsed() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileOnly', 'compileClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output).containsOnlyOnce(
            "quasar: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "cordaRuntime: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "compileOnly: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "compileClasspath: quasar-core-$QUASAR_VERSION-jdk8.jar".toString()
        )
    }

    @Test
    void checkOverriddenVersionIsUsed() {
        def quasarVersion = "0.7.9"
        assertThat(quasarVersion).isNotEqualTo(QUASAR_VERSION)

        def output = runGradleFor """
buildscript {
    ext {
        quasar_group = 'co.paralleluniverse'
        quasar_version = '$quasarVersion'
    }
}

plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileOnly', 'compileClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output).containsOnlyOnce(
            "quasar: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "cordaRuntime: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "compileOnly: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "compileClasspath: quasar-core-$quasarVersion-jdk8.jar".toString()
        )
    }

    @Test
    void checkForTransitiveDependencies() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
apply from: 'repositories.gradle'

apply plugin: 'net.corda.plugins.quasar-utils'

dependencies {
    testImplementation 'junit:junit:4.12'
}

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileClasspath', 'compileOnly', 'runtimeClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output.findAll { it.startsWith("quasar:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("cordaRuntime:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("compileOnly:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("compileClasspath:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("runtimeClasspath:") }.size()).isGreaterThan(1)
    }

    @Test
    void checkJVMArgsAddedForTests() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith("quasar-core-$QUASAR_VERSION-jdk8.jar")
        }.anyMatch {
            it == "TEST-JVM: -Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }

    @Test
    void checkOverriddenExclusionsAreUsed() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

apply from: 'repositories.gradle'

ext {
    quasar_exclusions = [ 'co.paralleluniverse**' ]
}

apply plugin: 'net.corda.plugins.quasar-utils'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    excludePackages.addAll 'groovy**', 'org.junit.**'
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(co.paralleluniverse**;groovy**;org.junit.**)')
        }
    }

    @Test
    void checkAddingExclusionsWithoutAnyDefaults() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    excludePackages.addAll 'groovy**', 'org.junit.**'
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(groovy**;org.junit.**)')
        }
    }

    @Test
    void checkDefaultExclusionsCanBeReplaced() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

apply from: 'repositories.gradle'

ext {
    quasar_exclusions = [ 'groovy**', 'java**' ]
}

apply plugin: 'net.corda.plugins.quasar-utils'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    excludePackages = [ 'co.paralleluniverse**', 'org.junit.**' ]
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(co.paralleluniverse**;org.junit.**)')
        }
    }

    @Test
    void checkValidationForQuasarExclusionsProperty() {
        def result = runnerFor("""
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

apply from: 'repositories.gradle'

ext {
    quasar_exclusions = 'stringy thing'
}

apply plugin: 'net.corda.plugins.quasar-utils'
""").buildAndFail()

        def output = result.output
        println output

        def lines = output.readLines()
        assertThat(lines).anyMatch {
            it.contains "quasar_exclusions property must be an Iterable<String>"
        }
    }

    @Test
    void testEnableVerboseOption() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}
apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    verbose = true
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=v')
        }
    }

    @Test
    void testEnableDebugOption() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils' apply false
}
apply plugin: 'net.corda.plugins.quasar-utils'
apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    debug = true
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.forEach {
            println "TEST-JVM: \${it}"
        }
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=d')
        }
    }

    private List<String> runGradleFor(String script) {
        def result = runnerFor(script).build()
        println result.output

        def build = result.task(":test")
        assertThat(build).isNotNull()
        assertThat(build.outcome).isEqualTo(SUCCESS)

        return result.output.readLines()
    }

    private GradleRunner runnerFor(String script) {
        def buildFile = testProjectDir.resolve("build.gradle")
        buildFile.text = script
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("--info", "--stacktrace", "test", "-g", TEST_GRADLE_USER_HOME)
            .withPluginClasspath()
            .withDebug(true)
    }
}
