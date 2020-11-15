package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class VerifyNotProvidedDependencyTest {
    companion object {
        private const val cordappVersion = "1.0.9.SNAPSHOT"

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("verify-not-provided")
                .withSubResource("src/main/java/com/example/unprovided/Host.java")
                .buildAndFail(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pannotations_version=$annotationsVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion"
                )
        }
    }

    @Test
    fun verifyCordaProvidedDependency() {
        assertThat(testProject.dependencyConstraints).isEmpty()
        assertThat(testProject.outcomeOf("verifyBundle")).isEqualTo(FAILED)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        assertThat(testProject.outcomeOf("cpk")).isNull()

        assertThat(testProject.output).contains(
            "Bundle verify-not-provided-$cordappVersion.jar has validation errors:",
            "Import Package clause found for missing package [com.example.annotations]"
        )
    }
}
