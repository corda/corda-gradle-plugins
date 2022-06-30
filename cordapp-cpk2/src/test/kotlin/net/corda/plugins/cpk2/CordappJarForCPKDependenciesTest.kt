package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Generating the CPKDependencies file requires that Gradle
 * also build the "main" jar file for any dependent CPKs.
 */
@TestInstance(PER_CLASS)
class CordappJarForCPKDependenciesTest {
    private companion object {
        private const val CPK_DEPENDENCIES_TASK_NAME = "cordappCPKDependencies"
        private const val cordappVersion = "1.2.1-SNAPSHOT"
        private const val hostVersion = "2.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-transitive-deps")
            .withSubResource("cordapp/build.gradle")
            .withTaskName(CPK_DEPENDENCIES_TASK_NAME)
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
    fun testCPKDependenciesBuildsDependentJar() {
        assertThat(testProject.outcomeOf(CPK_DEPENDENCIES_TASK_NAME))
            .isEqualTo(SUCCESS)
        assertThat(testProject.artifactDir)
            .doesNotExist()
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)
    }
}
