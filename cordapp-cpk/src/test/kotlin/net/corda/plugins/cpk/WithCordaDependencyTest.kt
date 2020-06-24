package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WithCordaDependencyTest {
    companion object {
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("with-corda-dependency")
                .withSubResource("library/build.gradle")
                .buildAndFail(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_release_version=$cordaReleaseVersion"
                )
        }
    }

    @Test
    fun hasCordappDependencyTest() {
        assertThat(testProject.outcomeOf("cordappDependencyConstraints")).isNull()
        assertThat(testProject.outcomeOf("jar")).isNull()
        assertThat(testProject.outcomeOf("cpk")).isNull()
    }
}
