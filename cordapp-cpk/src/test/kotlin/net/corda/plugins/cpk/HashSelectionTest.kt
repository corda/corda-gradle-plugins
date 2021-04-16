package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HashSelectionTest {
    companion object {
        const val cordappVersion = "1.2.5-SNAPSHOT"
        const val SHA3_256 = "SHA3-256"

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("hash-selection")
                .withSubResource("cordapp/build.gradle")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion",
                    "-Pcommons_codec_version=$commonsCodecVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Pcordapp_version=$cordappVersion"
                )
        }
    }

    @Test
    fun usesCorrectHashAlgorithm() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.fileName == "commons-codec-$commonsCodecVersion.jar" }
            .allMatch { it.hash.algorithm == SHA3_256 }
            .hasSize(1)
        assertThat(testProject.cpkDependencies)
            .allMatch { dep -> dep.signers.isNotEmpty() && dep.signers.all { it.algorithm == SHA3_256 } }
            .hasSize(1)
    }
}
