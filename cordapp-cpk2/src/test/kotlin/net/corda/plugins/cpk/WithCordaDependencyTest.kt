package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class WithCordaDependencyTest {
    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("with-corda-dependency")
            .withSubResource("library/build.gradle")
            .buildAndFail(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_release_version=$cordaReleaseVersion"
            )
    }

    @Test
    fun hasCordaDependency() {
        assertThat(testProject.outcomeOf("cordappDependencyConstraints")).isNull()
        assertThat(testProject.outcomeOf("cordappCPKDependencies")).isNull()
        assertThat(testProject.outcomeOf("jar")).isNull()
        assertThat(testProject.outcomeOf("cpk")).isNull()

        assertThat(testProject.output).contains(
            "CorDapp must not contain 'net.corda:corda-core:$cordaReleaseVersion'"
        )
    }
}
