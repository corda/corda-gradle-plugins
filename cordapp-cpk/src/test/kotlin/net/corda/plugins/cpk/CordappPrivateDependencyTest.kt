package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.io.StringReader
import java.nio.file.Path

class CordappPrivateDependencyTest {
    companion object {
        private const val cordappVersion = "1.2.1-SNAPSHOT"
        private const val hostVersion = "2.0.1-SNAPSHOT"
        private const val dependencyPrefix = "DEPENDENCY "

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
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
    }

    @Test
    fun testCordappPrivateDependencies() {
        assertThat(testProject.dependencyConstraints)
            .isEmpty()
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .allMatch { it.signedBy.isSHA256 }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

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
