@file:JvmName("ProjectConstants")
package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.plugins.BasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestReporter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors.toList
import kotlin.test.fail

const val expectedCordappContractVersion = 2
const val cordaReleaseVersion = "4.5"

@Suppress("unused", "MemberVisibilityCanBePrivate")
class GradleProject(private val projectDir: Path, private val reporter: TestReporter) {
    private companion object {
        private const val DEFAULT_TASK_NAME = ASSEMBLE_TASK_NAME
        private val testGradleUserHome = systemProperty("test.gradle.user.home")

        fun systemProperty(name: String): String = System.getProperty(name) ?: fail("System property '$name' not set.")

        @Throws(IOException::class)
        private fun installResource(folder: Path, resourceName: String): Boolean {
            val buildFile = folder.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
            return copyResourceTo(resourceName, buildFile) >= 0
        }

        @Throws(IOException::class)
        private fun copyResourceTo(resourceName: String, target: Path): Long {
            return GradleProject::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            } ?: -1
        }
    }

    private lateinit var result: BuildResult
    private var buildScript: String = ""
    private var taskName: String = DEFAULT_TASK_NAME
    private var testName: String = "."

    fun withTestName(testName: String): GradleProject {
        this.testName = testName
        return this
    }

    fun withTaskName(taskName: String): GradleProject {
        this.taskName = taskName
        return this
    }

    fun withSubResource(resourceName: String): GradleProject {
        installResource(subDirectoryFor(resourceName), "$testName/$resourceName")
        return this
    }

    private fun subDirectoryFor(resourceName: String): Path {
        var directory = projectDir
        var startIdx = 0
        while (true) {
            val endIdx = resourceName.indexOf('/', startIdx)
            if (endIdx == -1) {
                break
            }
            directory = Files.createDirectory(directory.resolve(resourceName.substring(startIdx, endIdx)))
            startIdx = endIdx + 1
        }
        return directory
    }

    fun withBuildScript(buildScript: String): GradleProject {
        this.buildScript = buildScript
        return this
    }

    val buildDir: Path = projectDir.resolve("build")
    val artifactDir: Path = buildDir.resolve("libs")
    val artifacts: List<Path> get() = Files.list(artifactDir).collect(toList())

    val dependencyConstraintsFile: Path = buildDir.resolve("generated-constraints")
        .resolve("META-INF").resolve("DependencyConstraints")
    val dependencyConstraints: List<String> get() = dependencyConstraintsFile.toFile().bufferedReader().readLines()

    var output: String = ""
        private set

    fun resultFor(taskName: String): BuildTask {
        return result.task(":$taskName") ?: fail("No outcome for $taskName task")
    }

    fun outcomeOf(taskName: String): TaskOutcome? {
        return result.task(":$taskName")?.outcome
    }

    private fun configureGradle(builder: (GradleRunner) -> BuildResult, args: Array<out String>) {
        installResource(projectDir, "repositories.gradle")
        installResource(projectDir, "gradle.properties")
        if (!installResource(projectDir, "$testName/settings.gradle")) {
            installResource(projectDir, "settings.gradle")
        }
        if (!installResource(projectDir, "$testName/build.gradle")) {
            projectDir.resolve("build.gradle").toFile().writeText(buildScript)
        }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(getGradleArgs(args))
            .withPluginClasspath()
            .withDebug(true)
        result = builder(runner)

        output = result.output
        reporter.publishEntry("stdout", output)
        println(output)
    }

    fun build(vararg args: String): GradleProject {
        configureGradle(GradleRunner::build, args)
        assertThat(buildDir).isDirectory()
        assertThat(artifactDir).isDirectory()
        assertEquals(SUCCESS, resultFor(taskName).outcome)
        return this
    }

    fun buildAndFail(vararg args: String): GradleProject {
        configureGradle(GradleRunner::buildAndFail, args)
        return this
    }

    private fun getGradleArgs(args: Array<out String>): List<String> {
        return arrayListOf(taskName, "--info", "--stacktrace", "-g", testGradleUserHome, *args)
    }
}
