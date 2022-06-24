package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CordappTransitiveDependencyTest {
    private companion object {
        private const val cordappVersion = "1.2.1-SNAPSHOT"
        private const val hostVersion = "2.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-transitive-deps")
            .withSubResource("cordapp/build.gradle")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_collections_version=$commonsCollectionsVersion",
                "-Pcommons_codec_version=$commonsCodecVersion",
                "-Pannotations_version=$annotationsVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Phost_version=$hostVersion"
            )
    }

    @Test
    fun testCordappTransitiveDependencies() {
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile
        assertThat(cordapp.hashOfEntry(CPK_DEPENDENCIES))
            .isEqualTo(testProject.cpkDependenciesHash)
    }
}
