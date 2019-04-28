package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.fail

class MetaFixConfigurationTests {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    private lateinit var output: String

    @Before
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
        testProjectDir.newFile("build.gradle").writeText(script)
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(getBasicArgsForTasks("metafix"))
            .withPluginClasspath()
    }

    private fun BuildResult.forTask(name: String): BuildTask {
        return task(":$name") ?: fail("No outcome for $name task")
    }
}