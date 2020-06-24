package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EagerDependenciesTest {
    companion object {
        private const val taskName = "cordappDependencyConstraints"
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("eager-dependencies")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion"
                )
        }
    }

    @Test
    fun eagerDependenciesTest() {
        val result = testProject.resultFor(taskName)
        assertEquals(SUCCESS, result.outcome)

        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("commons-io-2.7.jar,") }
            .hasSize(1)
    }
}