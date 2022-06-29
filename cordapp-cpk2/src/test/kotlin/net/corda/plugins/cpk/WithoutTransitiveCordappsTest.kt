package net.corda.plugins.cpk

import java.io.StringReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/**
 * Verify that non-transitive cordapp and cordaProvided
 * dependencies are NOT inherited by downstream CPK projects.
 */
@TestInstance(PER_CLASS)
class WithoutTransitiveCordappsTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpk2Version = "2.0-SNAPSHOT"
        private const val providedPrefix = "ALL-PROVIDED: "
    }

    private fun buildProject(
        taskName: String,
        testProjectDir: Path,
        reporter: TestReporter
    ): GradleProject {
        val repositoryDir = testProjectDir.resolve("maven")
        return GradleProject(testProjectDir, reporter)
            .withTestName("without-transitive-cordapps")
            .withSubResource("cpk-one/build.gradle")
            .withSubResource("cpk-two/build.gradle")
            .withTaskName(taskName)
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_codec_version=$commonsCodecVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcpk1_version=$cpk1Version",
                "-Pcpk2_version=$cpk2Version",
                "-Prepository_dir=$repositoryDir"
            )
    }

    @ParameterizedTest
    @ValueSource(strings = [ "assemble", "publishAllPublicationsToTestRepository" ])
    fun transitivesTest(
        taskName: String,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val testProject = buildProject(taskName, testProjectDir, reporter)

        assertThat(testProject.dependencyConstraints)
            .isEmpty()
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cpk-two" && it.version == toOSGi(cpk2Version) }
            .noneMatch { it.name == "com.example.cpk-one" }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile
        assertThat(cordapp.hashOfEntry(CPK_DEPENDENCIES))
            .isEqualTo(testProject.cpkDependenciesHash)

        val providedDeps = StringReader(testProject.output)
            .readLines()
            .filter { it.startsWith(providedPrefix) }
            .map { it.removePrefix(providedPrefix) }
        assertThat(providedDeps)
            .contains("commons-codec:$commonsCodecVersion")
            .hasSize(1)
    }
}
