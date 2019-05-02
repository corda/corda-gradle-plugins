package net.corda.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.assertEquals
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class GradleProject(private val projectDir: TemporaryFolder) : TestRule {
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

    fun pathOf(vararg elements: String): Path = Paths.get(projectDir.root.absolutePath, *elements)

    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                installResource(projectDir, "repositories.gradle")
                installResource(projectDir, "settings.gradle")
                installResource(projectDir, "gradle.properties")
                projectDir.newFile("build.gradle").writeText(buildScript)

                val result = GradleRunner.create()
                    .withProjectDir(projectDir.root)
                    .withArguments(getGradleArgsForTasks(taskName))
                    .withPluginClasspath()
                    .withDebug(true)
                    .build()
                output = result.output
                println(output)

                val taskResult = result.task(":$taskName") ?: fail("No outcome for $taskName task")
                assertEquals(SUCCESS, taskResult.outcome)

                statement.evaluate()
            }
        }
    }

    private fun getGradleArgsForTasks(vararg taskNames: String): List<String> {
        return arrayListOf(*taskNames, "--info", "--stacktrace", "-g", testGradleUserHome)
    }
}