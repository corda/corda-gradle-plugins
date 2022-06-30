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
class CordappWithNonBundleLibraryTest {
    private companion object {
        private const val cordappVersion = "4.2.5-SNAPSHOT"
        private const val slf4jVersion = "1.7.36"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-non-bundle-library")
            .buildAndFail(
                "-Pcommons_collections_version=$commonsCollectionsVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pslf4j_version=$slf4jVersion"
            )
    }

    @Test
    fun testLibraryIsRejected() {
        assertThat(testProject.outcomeOf("verifyLibraries")).isEqualTo(FAILED)
        assertThat(testProject.outputLines).anyMatch { line ->
            line.startsWith("Library embeddable-library.jar is not an OSGi bundle.")
        }
    }
}
