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
                    "-Pcorda_api_version=$cordaApiVersion",
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
            .anyMatch { it.fileName == "guava-$guavaVersion.jar" }
            .noneMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .noneMatch { it.fileName == "library.jar" }
            .noneMatch { it.fileName == "cordapp.jar" }
            .noneMatch { it.fileName.startsWith("slf4j-api-") }
            .allMatch { it.hash.isSHA256 }
            .hasSizeGreaterThanOrEqualTo(1)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi("0") }
            .allMatch { it.signedBy.isSHA256 }
            .hasSize(1)

        if (libraryGuavaVersion != guavaVersion) {
            assertThat(testProject.dependencyConstraints)
                .noneMatch { it.fileName == "guava-$libraryGuavaVersion.jar" }
        }

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()
    }
}
