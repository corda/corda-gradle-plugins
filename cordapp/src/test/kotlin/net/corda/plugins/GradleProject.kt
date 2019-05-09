package net.corda.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestReporter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class GradleProject(private val projectDir: Path, private val reporter: TestReporter) {
    private companion object {
        private val testGradleUserHome = systemProperty("test.gradle.user.home")
    }

    private var buildScript: String = ""
    private var taskName: String = "jar"

    fun withTaskName(taskName: String): GradleProject {
        this.taskName = taskName
        return this
    }

    fun withBuildScript(buildScript: String): GradleProject {
        this.buildScript = buildScript
        return this
    }

    var output: String = ""
        private set

    fun pathOf(vararg elements: String): Path = Paths.get(projectDir.toAbsolutePath().toString(), *elements)

    fun build(): GradleProject {
        installResource(projectDir, "repositories.gradle")
        installResource(projectDir, "settings.gradle")
        installResource(projectDir, "gradle.properties")
        projectDir.resolve("build.gradle").toFile().writeText(buildScript)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(getGradleArgsForTasks(taskName))
            .withPluginClasspath()
            .withDebug(true)
            .build()
        output = result.output
        reporter.publishEntry("stdout", output)
        println(output)

        val taskResult = result.task(":$taskName") ?: fail("No outcome for $taskName task")
        assertEquals(SUCCESS, taskResult.outcome)
        return this
    }

    private fun getGradleArgsForTasks(vararg taskNames: String): List<String> {
        return arrayListOf(*taskNames, "--info", "--stacktrace", "-g", testGradleUserHome)
    }
}
