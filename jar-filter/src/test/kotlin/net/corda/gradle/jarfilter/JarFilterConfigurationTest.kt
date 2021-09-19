package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.NO_SOURCE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JarFilterConfigurationTest {
    private companion object {
        private const val AMBIGUOUS = "net.corda.gradle.jarfilter.Ambiguous"
        private const val DELETE = "net.corda.gradle.jarfilter.DeleteMe"
        private const val REMOVE = "net.corda.gradle.jarfilter.RemoveMe"
        private const val STUB = "net.corda.gradle.jarfilter.StubMeOut"
    }

    @TempDir
    lateinit var testProjectDir: Path

    private lateinit var output: String

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
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    annotations {
            |        forDelete = ["$DELETE"]
            |    }
            |}
            |""".trimMargin()).build()
        output = result.output
        println(output)

        val jarFilter = result.forTask("jarFilter")
        assertEquals(NO_SOURCE, jarFilter.outcome)
    }

    @Test
    fun checkWithMissingJar() {
        val result = gradleProject("""
            |plugins {
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = file('does-not-exist.jar')
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        val exceptions = output.reader().readLines()
            .filter { it.startsWith("Caused by: ") }
            .map(::extractExceptionName)

        assertThat(exceptions).hasSize(2)
        assertThat(exceptions[0]).isEqualTo("org.gradle.api.InvalidUserCodeException")
        assertThat(exceptions[1]).isIn("java.io.FileNotFoundException", "java.nio.file.NoSuchFileException")

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
    }

    // The exception class name comes after "Caused by:" and is followed by another ':'.
    private fun extractExceptionName(text: String): String {
        val startIdx = text.indexOf(':') + 1
        val endIdx = text.indexOf(':', startIdx)
        return text.substring(startIdx, endIdx).trim()
    }

    @Test
    fun checkSameAnnotationForRemoveAndDelete() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forDelete = ["$AMBIGUOUS"]
            |        forRemove = ["$AMBIGUOUS"]
            |    }
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSequence(
            "Caused by: org.gradle.api.InvalidUserDataException: Annotation 'net.corda.gradle.jarfilter.Ambiguous' also appears in JarFilter 'forDelete' section"
        )

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
    }

    @Test
    fun checkSameAnnotationForRemoveAndStub() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forStub = ["$AMBIGUOUS"]
            |        forRemove = ["$AMBIGUOUS"]
            |    }
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSequence(
            "Caused by: org.gradle.api.InvalidUserDataException: Annotation 'net.corda.gradle.jarfilter.Ambiguous' also appears in JarFilter 'forStub' section"
        )

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
    }

    @Test
    fun checkSameAnnotationForStubAndDelete() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forStub = ["$AMBIGUOUS"]
            |        forDelete = ["$AMBIGUOUS"]
            |    }
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSequence(
            "Caused by: org.gradle.api.InvalidUserDataException: Annotation 'net.corda.gradle.jarfilter.Ambiguous' also appears in JarFilter 'forStub' section"
        )

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
    }

    @Test
    fun checkSameAnnotationForStubAndDeleteAndRemove() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forStub = ["$AMBIGUOUS"]
            |        forDelete = ["$AMBIGUOUS"]
            |        forRemove = ["$AMBIGUOUS"]
            |    }
            |}
            |""".trimMargin()).buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSequence(
            "Caused by: org.gradle.api.InvalidUserDataException: Annotation 'net.corda.gradle.jarfilter.Ambiguous' also appears in JarFilter 'forDelete' section"
        )

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
    }

    @Test
    fun checkRepeatedAnnotationForDelete() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forDelete = ["$DELETE", "$DELETE"]
            |    }
            |}
            |""".trimMargin()).build()
        output = result.output
        println(output)

        val jarFilter = result.forTask("jarFilter")
        assertEquals(SUCCESS, jarFilter.outcome)
    }

    @Test
    fun checkRepeatedAnnotationForStub() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forStub = ["$STUB", "$STUB"]
            |    }
            |}
            |""".trimMargin()).build()
        output = result.output
        println(output)

        val jarFilter = result.forTask("jarFilter")
        assertEquals(SUCCESS, jarFilter.outcome)
    }

    @Test
    fun checkRepeatedAnnotationForRemove() {
        val result = gradleProject("""
            |plugins {
            |    id 'java'
            |    id 'net.corda.plugins.jar-filter'
            |}
            |
            |import net.corda.gradle.jarfilter.JarFilterTask
            |task jarFilter(type: JarFilterTask) {
            |    jars = jar
            |    annotations {
            |        forRemove = ["$REMOVE", "$REMOVE"]
            |    }
            |}
            |""".trimMargin()).build()
        output = result.output
        println(output)

        val jarFilter = result.forTask("jarFilter")
        assertEquals(SUCCESS, jarFilter.outcome)
    }

    private fun gradleProject(script: String): GradleRunner {
        testProjectDir.resolve("build.gradle").toFile().writeText(script)
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(getBasicArgsForTasks("jarFilter"))
            .withDebug(isDebuggable(GradleVersion.current()))
            .withPluginClasspath()
    }

    private fun BuildResult.forTask(name: String): BuildTask {
        return task(":$name") ?: fail("No outcome for $name task")
    }
}