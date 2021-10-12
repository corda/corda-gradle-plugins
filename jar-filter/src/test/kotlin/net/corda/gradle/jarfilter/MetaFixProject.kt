package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.io.FileNotFoundException
import java.nio.file.Path

@Suppress("UNUSED")
class MetaFixProject(private val projectDir: Path, private val name: String) {
    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _metafixedJar: Path? = null
    val metafixedJar: Path get() = _metafixedJar ?: throw FileNotFoundException("Output not found")

    var output: List<String> = emptyList()
        private set

    fun build(): MetaFixProject {
        projectDir.installResources(
            "$name/build.gradle",
            "gradle.properties",
            "settings.gradle",
            "kotlin.gradle"
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(getGradleArgsForTasks("metafix"))
            .withPluginClasspath()
            .withDebug(true)
            .build()
        println(result.output)
        output = result.output.lines()

        val metafix = result.task(":metafix") ?: fail("No outcome for metafix task")
        assertEquals(SUCCESS, metafix.outcome)

        _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
        assertThat(sourceJar).isRegularFile()

        _metafixedJar = projectDir.pathOf("build", "metafixer-libs", "$name-metafixed.jar")
        assertThat(metafixedJar).isRegularFile()

        return this
    }
}
