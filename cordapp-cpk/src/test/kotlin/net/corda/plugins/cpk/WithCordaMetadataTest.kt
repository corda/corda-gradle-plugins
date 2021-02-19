package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.Manifest

class WithCordaMetadataTest {
    companion object {
        const val CONTRACT_CORDAPP_VERSION = "1.1.1-SNAPSHOT"
        const val WORKFLOW_CORDAPP_VERSION = "2.2.2-SNAPSHOT"
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("with-corda-metadata")
                .withSubResource("contracts/build.gradle")
                .withSubResource("contracts/src/main/kotlin/com/example/metadata/contracts/ExampleContract.kt")
                .withSubResource("workflows/build.gradle")
                .withSubResource("workflows/src/main/kotlin/com/example/metadata/workflows/ExampleFlow.kt")
                .build(
                    "-Pcontract_name=With Contract Metadata",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcontract_cordapp_version=$CONTRACT_CORDAPP_VERSION",
                    "-Pworkflow_name=With Workflow Metadata",
                    "-Pcordapp_workflow_version=$expectedCordappWorkflowVersion",
                    "-Pworkflow_cordapp_version=$WORKFLOW_CORDAPP_VERSION"
                )
        }

        val Path.manifest: Manifest get() = JarFile(toFile()).use(JarFile::getManifest)
    }

    @Test
    fun testAllArtifactsPresent() {
        assertThat(testProject.artifacts)
            .anyMatch { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
            .anyMatch { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
            .anyMatch { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION-cordapp.cpk") }
            .anyMatch { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION-cordapp.cpk") }
            .hasSize(4)
    }

    @Test
    fun verifyContractMetadata() {
        val contractJar = testProject.artifacts.single { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
        assertThat(contractJar).isRegularFile()
        val mainAttributes = contractJar.manifest.mainAttributes
        assertThat(mainAttributes.getValue(CORDA_CONTRACT_CLASSES))
            .isEqualTo("com.example.metadata.contracts.ExampleContract,com.example.metadata.contracts.ExampleContract\$NestedContract")
        assertThat(mainAttributes.getValue(CORDA_WORKFLOW_CLASSES))
            .isNull()
        assertThat(mainAttributes.getValue(CORDAPP_CONTRACT_NAME))
            .isEqualTo("With Contract Metadata")
        assertThat(mainAttributes.getValue(CORDAPP_CONTRACT_VERSION))
            .isEqualTo(expectedCordappContractVersion.toString())
        assertThat(mainAttributes.getValue(BUNDLE_VERSION))
            .isEqualTo(toOSGi(CONTRACT_CORDAPP_VERSION))
    }

    @Test
    fun verifyWorkflowMetadata() {
        val workflowJar = testProject.artifacts.single { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
        assertThat(workflowJar).isRegularFile()
        val mainAttributes = workflowJar.manifest.mainAttributes
        assertThat(mainAttributes.getValue(CORDA_WORKFLOW_CLASSES))
            .isEqualTo("com.example.metadata.workflows.ExampleFlow,com.example.metadata.workflows.ExampleFlow\$NestedFlow")
        assertThat(mainAttributes.getValue(CORDA_CONTRACT_CLASSES))
            .isNull()
        assertThat(mainAttributes.getValue(CORDAPP_WORKFLOW_NAME))
            .isEqualTo("With Workflow Metadata")
        assertThat(mainAttributes.getValue(CORDAPP_WORKFLOW_VERSION))
            .isEqualTo(expectedCordappWorkflowVersion.toString())
        assertThat(mainAttributes.getValue(BUNDLE_VERSION))
            .isEqualTo(toOSGi(WORKFLOW_CORDAPP_VERSION))
    }
}