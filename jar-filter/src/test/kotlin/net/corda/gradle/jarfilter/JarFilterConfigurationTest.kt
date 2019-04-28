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

class JarFilterConfigurationTest {
    private companion object {
        private const val AMBIGUOUS = "net.corda.gradle.jarfilter.Ambiguous"
        private const val DELETE = "net.corda.gradle.jarfilter.DeleteMe"
        private const val REMOVE = "net.corda.gradle.jarfilter.RemoveMe"
        private const val STUB = "net.corda.gradle.jarfilter.StubMeOut"
    }

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

        assertThat(output).containsSubsequence(
            "Caused by: org.gradle.api.InvalidUserCodeException:",
            "Caused by: java.io.FileNotFoundException:"
        )

        val jarFilter = result.forTask("jarFilter")
        assertEquals(FAILED, jarFilter.outcome)
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
        testProjectDir.newFile("build.gradle").writeText(script)
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(getBasicArgsForTasks("jarFilter"))
            .withPluginClasspath()
    }

    private fun BuildResult.forTask(name: String): BuildTask {
        return task(":$name") ?: fail("No outcome for $name task")
    }
}