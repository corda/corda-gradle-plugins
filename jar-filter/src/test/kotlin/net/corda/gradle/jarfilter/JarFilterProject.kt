package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.io.FileNotFoundException
import java.nio.file.Path

class JarFilterProject(private val projectDir: Path, private val name: String) {
    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _filteredJar: Path? = null
    val filteredJar: Path get() = _filteredJar ?: throw FileNotFoundException("Output not found")

    var output: List<String> = emptyList()
        private set

    fun build(): JarFilterProject {
        projectDir.installResources(
            "$name/build.gradle",
            "gradle.properties",
            "settings.gradle",
            "kotlin.gradle"
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(getGradleArgsForTasks("jarFilter"))
            .withPluginClasspath()
            .withDebug(true)
            .build()
        println(result.output)
        output = result.output.lines()

        val jarFilter = result.task(":jarFilter") ?: fail("No outcome for jarFilter task")
        assertEquals(SUCCESS, jarFilter.outcome)

        _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
        assertThat(sourceJar).isRegularFile()

        _filteredJar = projectDir.pathOf("build", "filtered-libs", "$name-filtered.jar")
        assertThat(filteredJar).isRegularFile()

        return this
    }
}
