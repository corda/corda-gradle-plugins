package net.corda.plugins.cpb

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors.toList
import net.corda.plugins.cpk.GradleProject
import net.corda.plugins.cpk.allSHA256
import net.corda.plugins.cpk.cordaApiVersion
import net.corda.plugins.cpk.expectedCordappContractVersion
import net.corda.plugins.cpk.toOSGi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir

class CpbPlatformTest {
    companion object {
        private const val platformCordappVersion = "3.4.2"
        private const val cordappVersion = "1.2.3"

        private lateinit var externalProject: GradleProject
        private lateinit var testProject: GradleProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            // JUnit 5.8+ should allow each use of @TempDir to create a
            // brand new directory. Replicate this behaviour until then.
            val mavenRepoDir = Files.createDirectory(testProjectDir.resolve("maven"))
            val externalCordappProjectDir = Files.createTempDirectory(testProjectDir.parent, "externalCordapp")
            externalProject = GradleProject(externalCordappProjectDir, reporter)
                .withTestName("external-cordapp")
                .withSubResource("corda-platform-cordapp/build.gradle")
                .withSubResource("external-cordapp-transitive-dependency/build.gradle")
                .withTaskName("publishAllPublicationsToTestRepository")
                .build("-Pmaven_repository_dir=$mavenRepoDir",
                       "-Pcorda_api_version=$cordaApiVersion",
                       "-Pplatform_cordapp_version=$platformCordappVersion",
                       "-Pcordapp_version=$cordappVersion",
                       "-Pcordapp_contract_version=$expectedCordappContractVersion")
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("cpb-with-platform")
                .withSubResource("src/main/kotlin/com/example/cpb/platform/ExampleContract.kt")
                .build("-Pmaven_repository_dir=$mavenRepoDir",
                       "-Pcorda_api_version=$cordaApiVersion",
                       "-Pcordapp_version=$cordappVersion",
                       "-Pcordapp_contract_version=$expectedCordappContractVersion")
        }

        // Only needed until JUnit 5.8+ allows multiple @TempDir directories.
        @AfterAll
        @JvmStatic
        fun tearDown() {
            externalProject.delete()
        }
    }

    @Test
    fun testCreatingCpbUsingPlatform() {
        assertThat(testProject.cpkDependencies)
            .anyMatch { cpk ->
                cpk.name == "net.corda.corda-platform-cordapp"
                    && cpk.version == toOSGi(platformCordappVersion)
                    && cpk.type == "corda-api" }
            .allMatch { it.signers.allSHA256 }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(3)

        val cpb = artifacts.single { it.toString().endsWith(".cpb") }
        assertThat(cpb).isRegularFile()

        val cpks = Files.list(testProject.buildDir.resolve("cpks")).collect(toList())
        assertThat(cpks)
            .anyMatch { cpk ->
                cpk.fileName.toString() == "corda-platform-cordapp-$platformCordappVersion-cordapp.cpk" }
            .hasSize(1)
    }
}
