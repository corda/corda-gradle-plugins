package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CpkExportReservedPackageTest2 {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cpk-export-reserved-package")
            .withSubResource("src/main/java/net/corda/contract/ExampleContract.java")
            .buildAndFail(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion"
            )
    }

    @Test
    fun testBuildFails() {
        assertThat(testProject.outcomeOf("verifyBundle")).isEqualTo(FAILED)
    }

    @Test
    fun testLogExplainsFailure() {
        assertThat(testProject.output).contains(
            "CorDapps must not export \"net.corda\" package"
        )
    }
}
