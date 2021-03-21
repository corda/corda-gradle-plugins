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
class MetaFixProject(private val projectDir: Path, private val name: String) {
    private var gradleVersion: GradleVersion = GradleVersion.current()

    fun withGradleVersion(version: GradleVersion): MetaFixProject {
        this.gradleVersion = version
        return this
    }

    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _metafixedJar: Path? = null
    val metafixedJar: Path get() = _metafixedJar ?: throw FileNotFoundException("Output not found")

    var output: List<String> = emptyList()
        private set

    fun build(): MetaFixProject {
        projectDir.installResources(
            "$name/build.gradle",
            "repositories.gradle",
            "gradle.properties",
            "settings.gradle",
            "kotlin.gradle",
            "java16.gradle"
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withGradleVersion(gradleVersion.version)
            .withArguments(getGradleArgsForTasks("metafix"))
            .withDebug(isDebuggable(gradleVersion))
            .withPluginClasspath()
            .build()
        println(result.output)
        output = result.output.lines()

        val metafix = result.task(":metafix") ?: fail("No outcome for metafix task")
        assertEquals(SUCCESS, metafix.outcome)

        _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
        assertThat(sourceJar).isRegularFile

        _metafixedJar = projectDir.pathOf("build", "metafixer-libs", "$name-metafixed.jar")
        assertThat(metafixedJar).isRegularFile

        return this
    }
}
