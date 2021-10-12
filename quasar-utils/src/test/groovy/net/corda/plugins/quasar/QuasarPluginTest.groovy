package net.corda.plugins.quasar

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
    private static final String QUASAR_CLASSIFIER = QuasarPlugin.defaultClassifier

    private static final String QUASAR_R3 = """\
if (org.gradle.api.JavaVersion.current().java9Compatible) {
    version = '0.8.0_r3'
    classifier = ''
} else {
    version = '0.7.12_r3'
}
"""

    @TempDir
    public Path testProjectDir

    @BeforeEach
    void setup() {
        Utilities.installResource(testProjectDir, "gradle.properties")
        Utilities.installResource(testProjectDir, "settings.gradle")
        Utilities.installResource(testProjectDir, "src/test/java/BasicTest.java")
    }

    @Test
    void checkDefaultVersionIsUsed() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to configurations'

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'cordaRuntimeOnly', 'compileOnly', 'compileClasspath', 'runtimeClasspath']
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
            "quasar: co.paralleluniverse:quasar-core:jar:${QUASAR_VERSION}:".toString(),
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core:jar:${QUASAR_VERSION}:".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core:jar:${QUASAR_VERSION}:".toString(),
            "compileOnly: co.paralleluniverse:quasar-core:jar:${QUASAR_VERSION}:".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core:jar:${QUASAR_VERSION}:".toString()
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
        quasar_version = '${quasarVersion}'
    }
}

plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to configurations'

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'cordaRuntimeOnly', 'compileOnly', 'compileClasspath', 'runtimeClasspath']
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
            "quasar: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "compileOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString()
        )
    }

    @Test
    void checkLocalOverriddenVersionIsUsed() {
        def quasarVersion = "0.7.9"
        assertThat(quasarVersion).isNotEqualTo(QUASAR_VERSION)

        def output = runGradleFor """
buildscript {
    ext {
        quasar_group = 'co.paralleluniverse'
    }
}

plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

quasar {
    version = '${quasarVersion}'
}

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'cordaRuntimeOnly', 'compileOnly', 'compileClasspath', 'runtimeClasspath']
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
            "quasar: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "compileOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:".toString()
        )
    }

    @Test
    void checkLocalOverriddenClassifierVersionIsUsed() {
        def quasarVersion = '0.7.12_r3'
        def quasarClassifier = 'jdk8'
        assertThat(quasarVersion).isNotEqualTo(QUASAR_VERSION)
        assertThat(quasarClassifier).isNotEqualTo(QUASAR_CLASSIFIER)

        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

quasar {
    group = 'co.paralleluniverse'
    version = '${quasarVersion}'
    classifier = '${quasarClassifier}'
}

task show {
    doFirst {
        def configs = configurations.matching {
            it.name in ['quasar', 'cordaRuntimeOnly', 'compileOnly', 'compileClasspath', 'runtimeClasspath']
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
            "quasar: co.paralleluniverse:quasar-core:jar:${quasarVersion}:${quasarClassifier}".toString(),
            "cordaRuntimeOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:${quasarClassifier}".toString(),
            "runtimeClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:${quasarClassifier}".toString(),
            "compileOnly: co.paralleluniverse:quasar-core:jar:${quasarVersion}:${quasarClassifier}".toString(),
            "compileClasspath: co.paralleluniverse:quasar-core:jar:${quasarVersion}:${quasarClassifier}".toString()
        )
    }

    @Test
    void checkForTransitiveDependencies() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to configurations'

task show {
    doFirst {
        def configs = configurations.matching { it.name in ['quasar', 'compileClasspath', 'runtimeClasspath'] }
        configs.collectEntries { [(it.name):it] }.each { name, files ->
            files.each { file ->
                println "\$name: \${file.name}"
            }
        }
    }
}
""", "show"
        assertThat(output.findAll { it.startsWith("quasar:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("compileClasspath:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("runtimeClasspath:") }.size()).isGreaterThan(1)
    }

    @Test
    void checkJVMArgsAddedForTests() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to test JVM arguments'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.contains("quasar-core-") && it.endsWith(".jar")
        }.anyMatch {
            it == "TEST-JVM: -Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }

    @Test
    void checkOverriddenExclusionsAreUsed() {
        def output = runGradleFor """
buildscript {
    ext {
        quasar_exclusions = [ 'co.paralleluniverse**' ]
    }
}

plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to test JVM arguments'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
    excludePackages.addAll 'groovy**', 'org.junit.**'
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(co.paralleluniverse**;groovy**;org.junit.**)')
        }
    }

    @Test
    void checkAddingExclusionsWithoutAnyDefaults() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to test JVM arguments'

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
    excludePackages.addAll 'groovy**', 'org.junit.**'
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(groovy**;org.junit.**)')
        }
    }

    @Test
    void checkDefaultExclusionsCanBeReplaced() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to test JVM arguments'

ext {
    quasar_exclusions = [ 'groovy**', 'java**' ]
}

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
    excludePackages = [ 'co.paralleluniverse**', 'org.junit.**' ]
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=x(co.paralleluniverse**;org.junit.**)')
        }
    }

    @Test
    void checkValidationForQuasarExclusionsProperty() {
        def result = runnerFor("""
buildscript {
    ext {
        quasar_exclusions = 'stringy thing'
    }
}
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

description 'Show quasar-core added to test JVM arguments'
""", "test").buildAndFail()

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
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
    verbose = true
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=v')
        }
    }

    @Test
    void testEnableDebugOption() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
    ${QUASAR_R3}
    debug = true
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=d')
        }
    }

    @Test
    void testExcludeClassLoaders() {
        def output = runGradleFor """
plugins {
    id 'net.corda.plugins.quasar-utils'
    id 'java-library'
}

dependencies {
    testImplementation 'junit:junit:4.12'
}

quasar {
   ${QUASAR_R3}
    excludeClassLoaders = [ 'net.corda.**', 'org.testing.*' ]
}

jar {
    enabled = false
}

test {
    doLast {
        allJvmArgs.each {
            println "TEST-JVM: \${it}"
        }
    }
}
""", "test"
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith('=l(net.corda.**;org.testing.*)')
        }
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
