package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SimpleCordappTest {
    companion object {
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("simple-cordapp")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion"
                )
        }
    }

    @Test
    fun simpleTest() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("commons-io-2.7.jar") }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()
    }
}