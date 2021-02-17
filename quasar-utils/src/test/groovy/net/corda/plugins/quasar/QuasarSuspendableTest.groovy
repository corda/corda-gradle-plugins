package net.corda.plugins.quasar

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class QuasarSuspendableTest {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", ".")
    private static final String JUNIT_VERSION = "4.13"

    @TempDir
    public Path testProjectDir

    @BeforeEach
    void setup() {
        Utilities.installResource(testProjectDir, "gradle.properties")
        Utilities.installResource(testProjectDir, "settings.gradle")
        Utilities.installResource(testProjectDir, "repositories.gradle")
        Utilities.installResource(testProjectDir, "src/test/java/BasicTest.java")
    }

    @Test
    void testWithSuspendableAnnotation() {
        def quasarVersion = '0.8.4_r3'
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

apply from: 'repositories.gradle'

quasar {
    suspendableAnnotation = 'net.corda.base.annotations.Suspendable'
    version = '${quasarVersion}'
}

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'quasarAgent', 'cordaRuntimeOnly', 'compileOnly', 'cordaProvided', 'compileClasspath', 'runtimeClasspath']
        }
        configs.collectEntries { [(it.name):it.incoming.dependencies] }.each { name, dependencies ->
            dependencies.each { dep ->
                dep.artifacts.each { art ->
                    println "\$name: \${dep.group}:\${dep.name}:\${art.extension}:\${dep.version}:\${art.classifier ?: ''}"
                }
            }
        }
    }
}
""", "show"
        assertThat(output).containsOnlyOnce(
            "quasar: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "quasarAgent: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:agent".toString(),
        ).doesNotContain(
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "compileOnly: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "cordaProvided: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString()
        )
    }

    @Test
    void testWithEmptySuspendableAnnotation() {
        def quasarVersion = '0.8.4_r3'
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

apply from: 'repositories.gradle'

quasar {
    suspendableAnnotation = ''
    version = '${quasarVersion}'
}

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'quasarAgent', 'cordaRuntimeOnly', 'compileOnly', 'cordaProvided', 'compileClasspath', 'runtimeClasspath']
        }
        configs.collectEntries { [(it.name):it.incoming.dependencies] }.each { name, dependencies ->
            dependencies.each { dep ->
                dep.artifacts.each { art ->
                    println "\$name: \${dep.group}:\${dep.name}:\${art.extension}:\${dep.version}:\${art.classifier ?: ''}"
                }
            }
        }
    }

    doLast {
        println "QUASAR-ARG: [\${quasar.options.get()}]"
    }
}
""", "show"
        assertThat(output).containsOnlyOnce(
            "quasar: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "quasarAgent: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:agent".toString(),
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "compileOnly: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "cordaProvided: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core-osgi:jar:${quasarVersion}:".toString()
        )
        assertThat(output).containsOnlyOnce("QUASAR-ARG: []")
    }

    @Test
    void checkJVMArgsAddedForTests() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core-osgi added to test JVM arguments'

apply from: 'repositories.gradle'

dependencies {
    testImplementation 'junit:junit:${JUNIT_VERSION}'
}

quasar {
    suspendableAnnotation = 'net.corda.base.annotations.Suspendable'
}

jar {
    enabled = false
}

task show {
    doLast {
        println "QUASAR-ARG: [\${quasar.options.get()}]"
    }
}
""", "show"
        assertThat(output).containsOnlyOnce("QUASAR-ARG: [=a(SUSPENDABLE=net.corda.base.annotations.Suspendable)]")
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
