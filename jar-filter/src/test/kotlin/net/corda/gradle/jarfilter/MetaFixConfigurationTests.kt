package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MetaFixConfigurationTests {
    private lateinit var output: String

    @TempDir
    lateinit var testProjectDir: Path

    @BeforeEach
    fun setup() {
        testProjectDir.installResources("gradle.properties", "settings.gradle")
    }

    @Test
    fun checkNoJarMeansNoSource() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.MetaFixerTask
            |task metafix(type: MetaFixerTask)
            |""".trimMargin()).build()
        output = result.output
        println(output)

        val metafix = result.forTask("metafix")
        assertEquals(NO_SOURCE, metafix.outcome)
    }

    @Test
    fun checkWithMissingJar() {
        val result = gradleProject("""
            |plugins {
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.MetaFixerTask
            |task metafix(type: MetaFixerTask) {
            |    jars = file('does-not-exist.jar')
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSubsequence(
            "Caused by: org.gradle.api.InvalidUserCodeException:",
            "Caused by: java.io.FileNotFoundException:"
        )

        val metafix = result.forTask("metafix")
        assertEquals(FAILED, metafix.outcome)
    }

    private fun gradleProject(script: String): GradleRunner {
        testProjectDir.resolve("build.gradle").toFile().writeText(script)
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(getBasicArgsForTasks("metafix"))
            .withPluginClasspath()
    }

    private fun BuildResult.forTask(name: String): BuildTask {
        return task(":$name") ?: fail("No outcome for $name task")
    }
}