package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class WithDependentCordappTest {
    companion object {
        const val CORDA_GUAVA_VERSION = "20.0"

        private fun buildProject(guavaVersion: String, @TempDir testProjectDir: Path, reporter: TestReporter): GradleProject {
            return GradleProject(testProjectDir, reporter)
                .withTestName("with-dependent-cordapp")
                .withSubResource("library/build.gradle")
                .withSubResource("cordapp/build.gradle")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion",
                    "-Pguava_version=$guavaVersion"
                )
        }
    }

    @ParameterizedTest
    @CsvSource("19.0,$CORDA_GUAVA_VERSION", "28.2-jre,28.2-jre")
    fun hasCordappDependencyTest(
        guavaVersion: String,
        expectedGuavaVersion: String,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        assertThat(guavaVersion).isNotEqualTo(CORDA_GUAVA_VERSION)
        val testProject = buildProject(guavaVersion, testProjectDir, reporter)

        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("commons-io-2.7.jar") }
            .anyMatch { it.startsWith("guava-$expectedGuavaVersion.jar") }
            .anyMatch { it.startsWith("library.jar") }
            .anyMatch { it.startsWith("cordapp.jar") }
            .hasSizeGreaterThanOrEqualTo(4)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()
    }
}
