package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

class SignJarTest {
    @TempDir
    lateinit var testProjectDir: Path
    private lateinit var buildFile: Path

    private companion object {
        private const val SIGNING_TAG = "Jar signing with command: jarsigner"
        private val testGradleUserHome = systemProperty("test.gradle.user.home")
    }

    @BeforeEach
    fun setup() {
        buildFile = testProjectDir.resolve("build.gradle")
        installResource(testProjectDir, "settings.gradle")
        installResource(testProjectDir, "gradle.properties")
    }

    @Test
    fun `sign jar with default keystore`() {
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |    from sourceSets.main.output
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = true
            |    }
            |}
            |
            |task signTestJar(type: net.corda.plugins.SignJar) {
            |    inputJars jar
            |    dependsOn jar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create a simple Java source file
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("""
            |package com.example;
            |public class Test {
            |    public static void main(String[] args) {
            |        System.out.println("Hello");
            |    }
            |}
        """.trimMargin())

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJar", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        println(result.output)
        assertThat(result.task(":signTestJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Check that signed jar was created
        val signedJarPath = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "test-app-signed.jar")
        assertThat(signedJarPath).exists()
        assertThat(signedJarPath).isRegularFile()

        // Verify it's a valid JAR
        JarFile(signedJarPath.toFile()).use { jarFile ->
            assertThat(jarFile.entries().toList()).isNotEmpty()
        }

        // Verify signing message is logged
        assertThat(result.output).contains(SIGNING_TAG)
    }

    @Test
    fun `sign jar with custom postfix`() {
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = true
            |    }
            |}
            |
            |task signTestJar(type: net.corda.plugins.SignJar) {
            |    postfix = '-custom'
            |    inputJars jar
            |    dependsOn jar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create minimal Java source
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("package com.example; public class Test {}")

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJar", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        println(result.output)
        assertThat(result.task(":signTestJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Check that jar with custom postfix was created
        val signedJarPath = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "test-app-custom.jar")
        assertThat(signedJarPath).exists()
    }

    @Test
    fun `sign multiple jars`() {
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |}
            |
            |task createExtraJar(type: Jar) {
            |    archiveBaseName = 'extra-lib'
            |    from sourceSets.main.output
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = true
            |    }
            |}
            |
            |task signTestJars(type: net.corda.plugins.SignJar) {
            |    inputJars jar, createExtraJar
            |    dependsOn jar, createExtraJar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create minimal Java source
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("package com.example; public class Test {}")

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJars", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        println(result.output)
        assertThat(result.task(":signTestJars")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Check both jars were signed
        val signedJarPath1 = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "test-app-signed.jar")
        val signedJarPath2 = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "extra-lib-signed.jar")
        assertThat(signedJarPath1).exists()
        assertThat(signedJarPath2).exists()
    }

    @Test
    fun `signing passwords are not logged`() {
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = true
            |    }
            |}
            |
            |task signTestJar(type: net.corda.plugins.SignJar) {
            |    inputJars jar
            |    dependsOn jar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create minimal Java source
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("package com.example; public class Test {}")

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJar", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        println(result.output)

        // Verify signing passwords are masked
        assertThat(result.output.split("\n")).anyMatch { line ->
            line.startsWith(SIGNING_TAG)
        }.noneMatch { line ->
            line.startsWith(SIGNING_TAG)
                    && (line.matches("^.* keypass=[^*,]+,.*\$".toRegex()) || line.matches("^.* storepass=[^*,]+,.*\$".toRegex()))
        }
    }

    @Test
    fun `jar without extension gets suffix correctly`() {
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |    archiveExtension = ''
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = true
            |    }
            |}
            |
            |task signTestJar(type: net.corda.plugins.SignJar) {
            |    postfix = '-signed'
            |    inputJars jar
            |    dependsOn jar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create minimal Java source
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("package com.example; public class Test {}")

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJar", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .build()

        println(result.output)
        assertThat(result.task(":signTestJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Check that file without extension was signed with suffix at end
        val signedJarPath = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "test-app-signed")
        assertThat(signedJarPath).exists()
    }

    @Test
    fun `sign jar with provider classpath`() {
        val providerPath = "/path/to/provider.jar"
        val buildScript = """
            |plugins {
            |    id 'net.corda.plugins.cordapp'
            |    id 'java'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |jar {
            |    archiveBaseName = 'test-app'
            |    from sourceSets.main.output
            |}
            |
            |cordapp {
            |    targetPlatformVersion = 5
            |    signing {
            |        enabled = false
            |    }
            |}
            |
            |task signTestJar(type: net.corda.plugins.SignJar) {
            |    signing {
            |        options {
            |            providerClassPath = '$providerPath'
            |        }
            |    }
            |    inputJars jar
            |    dependsOn jar
            |}
        """.trimMargin()

        buildFile.toFile().writeText(buildScript)

        // Create minimal Java source
        val srcDir = testProjectDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        srcDir.resolve("Test.java").toFile().writeText("package com.example; public class Test {}")

        val result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("signTestJar", "-s", "--info", "-g", testGradleUserHome)
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()

        println(result.output)

        // Verify providerClassPath is included in the command
        // Note: buildAndFail because jarsigner doesn't support -providerClassPath on JDK 8
        // But we can still verify it was passed to the command
        assertThat(result.output).contains(SIGNING_TAG)
        assertThat(result.output).contains("-providerClassPath $providerPath")
    }
}