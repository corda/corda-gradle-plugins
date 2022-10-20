package net.corda.plugins.cpk2

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir

@TestInstance(PER_CLASS)
class CordappImportPackageTest {
    private companion object {
        private const val cordappVersion = "1.6.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withSubResource("src/main/java/com/example/importpackage/ExampleContract.java")
            .withTestName("cordapp-import-package")
            .buildAndFail(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion"
            )
    }

    @Test
    fun testImportPackageRequired() {
        assertThat(testProject.outcomeOf("jar")).isEqualTo(FAILED)
        assertThat(testProject.outputLines).anyMatch { line ->
            line.matches(".*\\Q org.gradle.api.InvalidUserDataException: Bnd instruction 'Import-Package=\\E(.*)\\Q' was replaced with '\\E(.*)'".toRegex())
        }
    }
}
