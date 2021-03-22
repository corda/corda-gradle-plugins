package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class WithDependentCordappTest {
    companion object {
        private const val CORDA_GUAVA_VERSION = "20.0"

        private fun buildProject(
            guavaVersion: String,
            libraryGuavaVersion: String,
            testProjectDir: Path,
            reporter: TestReporter
        ): GradleProject {
            return GradleProject(testProjectDir, reporter)
                .withTestName("with-dependent-cordapp")
                .withSubResource("library/build.gradle")
                .withSubResource("cordapp/build.gradle")
                .build(
                    "-Pcordapp_workflow_version=$expectedCordappWorkflowVersion",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Plibrary_guava_version=$libraryGuavaVersion",
                    "-Pguava_version=$guavaVersion"
                )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "$CORDA_GUAVA_VERSION,29.0-jre",
        "19.0,$CORDA_GUAVA_VERSION",
        "19.0,19.0",
        "28.2-jre,28.2-jre"
    )
    fun hasCordappDependency(
        guavaVersion: String,
        libraryGuavaVersion: String,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val testProject = buildProject(guavaVersion, libraryGuavaVersion, testProjectDir, reporter)

        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("guava-$guavaVersion.jar") }
            .noneMatch { it.startsWith("commons-io-$commonsIoVersion.jar") }
            .noneMatch { it.startsWith("library.jar") }
            .noneMatch { it.startsWith("cordapp.jar") }
            .noneMatch { it.startsWith("slf4j-api") }
            .hasSizeGreaterThanOrEqualTo(1)
        assertThat(testProject.cpkDependencies)
            .contains("com.example.cordapp,${toOSGi("0")}")
            .hasSize(1)

        if (libraryGuavaVersion != guavaVersion) {
            assertThat(testProject.dependencyConstraints)
                .noneMatch { it.startsWith("guava-$libraryGuavaVersion.jar") }
        }

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()
    }
}