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
class CordappOverrideVersionTest {
    private companion object {
        private const val cordappVersion = "1.6.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withSubResource("src/main/java/com/example/overrideversion/ExampleContract.java")
            .withTestName("cordapp-override-version")
            .buildAndFail(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion"
            )
    }

    @Test
    fun testCordappVersionRequired() {
        assertThat(testProject.outcomeOf("jar")).isEqualTo(FAILED)
        assertThat(testProject.outputLines).anyMatch { line ->
            line.contains(" org.gradle.api.InvalidUserDataException: Bnd instruction 'Corda-CPK-Cordapp-Version=\${Bundle-Version}' was replaced with 'NONSENSE'")
        }
    }
}
