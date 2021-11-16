package net.corda.plugins.cpk

import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import net.corda.plugins.cpk.xml.HashValue

class HashSelectionTest {
    companion object {
        const val cordappVersion = "1.2.5-SNAPSHOT"
        const val SHA3_256 = "SHA3-256"

        private lateinit var cordappProject: GradleProject
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(
            @TempDir testProjectDir: Path,
            @TempDir cordappDir: Path,
            reporter: TestReporter
        ) {
            val repositoryDir = Files.createDirectory(testProjectDir.resolve("maven"))
            cordappProject = GradleProject(cordappDir, reporter)
                .withTestName("hash-selection/cordapp")
                .withTaskName("publishAllPublicationsToTestRepository")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Prepository_dir=$repositoryDir"
                )
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("hash-selection")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion",
                    "-Pcommons_codec_version=$commonsCodecVersion",
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Prepository_dir=$repositoryDir"
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
            .allMatch { dep ->
                dep.signers.isNotEmpty() && dep.signers.all { it is HashValue && it.algorithm == SHA3_256 }
            }.hasSize(1)
    }
}
