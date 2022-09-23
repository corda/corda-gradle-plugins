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
class CordappEmbeddedOverridesTest {
    private companion object {
        private const val cordappVersion = "1.8.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withSubResource("src/main/java/com/example/embeddedoverrides/ExampleContract.java")
            .withTestName("cordapp-embedded-overrides")
            .buildAndFail(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion"
            )
    }

    @Test
    fun testEmbeddedJarsRequired() {
        assertThat(testProject.outcomeOf("jar")).isEqualTo(FAILED)
        assertThat(testProject.outputLines).anyMatch { line ->
            line.contains(" org.gradle.api.InvalidUserDataException: Bnd instructions {-includeresource.cordapp=") &&
            line.contains(", Bundle-ClassPath=.,") &&
            line.endsWith("} were replaced with {-includeresource.cordapp=@bongo.jar, Bundle-ClassPath=NONSENSE}.")
        }
    }
}
