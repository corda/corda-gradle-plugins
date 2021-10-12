package net.corda.plugins.quasar

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class QuasarNoInstrumentTest {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", ".")
    private static final String JUNIT_VERSION = "4.13.2"

    @TempDir
    public Path testProjectDir

    @BeforeEach
    void setup() {
        Utilities.installResource(testProjectDir, "gradle.properties")
        Utilities.installResource(testProjectDir, "settings.gradle")
        Utilities.installResource(testProjectDir, "src/main/java/org/testing/ExampleExec.java")
        Utilities.installResource(testProjectDir, "src/test/java/BasicTest.java")
    }

    @Test
    void testUninstrumentedTests() {
        def quasarVersion = '0.7.13_r3'
        def output = runGradleFor """\
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

quasar {
    version = '${quasarVersion}'
    instrumentTests = false
}

dependencies {
    testImplementation 'junit:junit:${JUNIT_VERSION}'
}

tasks.named('test', Test) {
    doLast {
        println "JVM-ARGS: \${jvmArgs}"
    }
}
""", "test"
        assertThat(output).containsOnlyOnce("JVM-ARGS: []")
    }

    @Test
    void testUninstrumentedJavaExec() {
        def quasarVersion = '0.7.13_r3'
        def output = runGradleFor """\
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

quasar {
    version = '${quasarVersion}'
    instrumentJavaExec = false
}

dependencies {
    testImplementation 'junit:junit:${JUNIT_VERSION}'
}

tasks.register('testExec', JavaExec) {
    dependsOn 'classes'
    mainClass = 'org.testing.ExampleExec'
    classpath = sourceSets.main.runtimeClasspath
    args 'one', 'two', 'three'
    doLast {
        println "JVM-ARGS: \${jvmArgs}"
    }
}
""", "testExec"
        assertThat(output)
            .containsOnlyOnce("ARGS: one,two,three")
            .containsOnlyOnce("JVM-ARGS: []")
    }

    private List<String> runGradleFor(String script, String taskName) {
        def result = runnerFor(script, taskName).build()
        println result.output

        def build = result.task(":$taskName")
        assertThat(build).isNotNull()
        assertThat(build.outcome).isEqualTo(SUCCESS)

        return result.output.readLines()
    }

    private GradleRunner runnerFor(String script, String taskName) {
        def buildFile = testProjectDir.resolve("build.gradle")
        buildFile.text = script
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("--info", "--stacktrace", taskName, "-g", TEST_GRADLE_USER_HOME)
            .withPluginClasspath()
            .withDebug(true)
    }
}