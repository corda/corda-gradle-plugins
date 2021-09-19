package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.io.FileNotFoundException
import java.nio.file.Path

@Suppress("unused")
class JarFilterProject(private val projectDir: Path, private val name: String) {
    private var gradleVersion: GradleVersion = GradleVersion.current()

    fun withGradleVersion(version: GradleVersion): JarFilterProject {
        this.gradleVersion = version
        return this
    }

    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _filteredJar: Path? = null
    val filteredJar: Path get() = _filteredJar ?: throw FileNotFoundException("Output not found")

    var output: List<String> = emptyList()
        private set

    fun build(): JarFilterProject {
        projectDir.installResources(
            "$name/build.gradle",
            "repositories.gradle",
            "gradle.properties",
            "settings.gradle",
            "kotlin.gradle",
            "javaLatest.gradle"
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withGradleVersion(gradleVersion.version)
            .withArguments(getGradleArgsForTasks("jarFilter"))
            .withDebug(isDebuggable(gradleVersion))
            .withPluginClasspath()
            .build()
        println(result.output)
        output = result.output.lines()

        val jarFilter = result.task(":jarFilter") ?: fail("No outcome for jarFilter task")
        assertEquals(SUCCESS, jarFilter.outcome)

        _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
        assertThat(sourceJar).isRegularFile

        _filteredJar = projectDir.pathOf("build", "filtered-libs", "$name-filtered.jar")
        assertThat(filteredJar).isRegularFile

        return this
    }
}
