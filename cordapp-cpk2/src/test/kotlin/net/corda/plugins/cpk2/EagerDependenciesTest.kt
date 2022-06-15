package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class EagerDependenciesTest {
    private companion object {
        private const val taskName = "cpk"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("eager-dependencies")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion"
            )
    }

    @Test
    fun eagerDependenciesTest() {
        val result = testProject.resultFor(taskName)
        assertEquals(SUCCESS, result.outcome)

        assertThat(testProject.libraries)
            .anyMatch { it == "commons-io-$commonsIoVersion.jar" }
            .hasSize(1)
    }
}
