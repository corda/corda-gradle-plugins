package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CordappTransitiveDependencyTest {
    companion object {
        private const val cordappVersion = "1.2.1-SNAPSHOT"
        private const val hostVersion = "2.0.1-SNAPSHOT"

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("cordapp-transitive-deps")
                .withSubResource("cordapp/build.gradle")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcommons_collections_version=$commonsCollectionsVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Phost_version=$hostVersion"
                )
        }
    }

    @Test
    fun testCordappTransitiveDependencies() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("commons-collections-$commonsCollectionsVersion.jar") }
            .anyMatch { it.startsWith("cordapp-$cordappVersion.jar") }
            .hasSize(2)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()
    }
}
