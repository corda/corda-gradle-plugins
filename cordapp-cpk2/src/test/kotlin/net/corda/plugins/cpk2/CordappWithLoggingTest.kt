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
class CordappWithLoggingTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val slf4jVersion = "2.0.0-alpha1"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-with-logging")
            .withSubResource("src/main/java/com/example/contract/LoggingContract.java")
            .buildAndFail(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pslf4j_version=$slf4jVersion"
            )
    }

    @Test
    fun conflictingLoggingVersionsTest() {
        assertThat(testProject.cpkDependencies).isEmpty()
        assertThat(testProject.outcomeOf("verifyBundle")).isEqualTo(FAILED)
        assertThat(testProject.output).contains(
            "Bundle cordapp-with-logging-${cordappVersion}.jar has validation errors:",
            "Import Package clause requires package [org.slf4j] with version '[2.0,3)', but version(s) '1.7.36' exported"
        )
    }
}
