package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.io.StringReader
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CordappPrivateDependencyTest {
    private companion object {
        private const val cordappVersion = "1.2.1-SNAPSHOT"
        private const val hostVersion = "2.0.1-SNAPSHOT"
        private const val dependencyPrefix = "DEPENDENCY "
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-private-deps")
            .withSubResource("cordapp/build.gradle")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_collections_version=$commonsCollectionsVersion",
                "-Pcommons_codec_version=$commonsCodecVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Phost_version=$hostVersion"
            )
    }

    @Test
    fun testCordappPrivateDependencies() {
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val providedDeps = StringReader(testProject.output)
            .readLines()
            .filter { it.startsWith(dependencyPrefix) }
            .map { it.removePrefix(dependencyPrefix) }
        assertThat(providedDeps)
            .contains("CORDAPP cordaPrivateProvided: commons-collections:$commonsCollectionsVersion")
            .contains("CORDAPP cordaAllProvided: commons-collections:$commonsCollectionsVersion")
            .contains("CORDAPP cordaAllProvided: corda-api:$cordaApiVersion")
            .contains("HOST cordaPrivateProvided: commons-codec:$commonsCodecVersion")
            .contains("HOST cordaAllProvided: commons-codec:$commonsCodecVersion")
            .contains("HOST cordaAllProvided: corda-api:$cordaApiVersion")
            .hasSize(6)
    }
}
