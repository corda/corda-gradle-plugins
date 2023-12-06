package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class WithCordappNameTest {
    private companion object {
        private const val CONTRACT_CORDAPP_VERSION = "1.1.1-SNAPSHOT"
        private const val CONTRACT_CORDAPP_CORDAPPNAME = "com.example.contracts.cpk"
        private const val WORKFLOW_CORDAPP_VERSION = "2.2.2-SNAPSHOT"
        private const val WORKFLOW_CORDAPP_CORDAPPNAME = "com.example.workflows.cpk"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("with-cordapp-name")
            .withSubResource("contracts/build.gradle")
            .withSubResource("contracts/src/main/kotlin/com/example/name/contracts/ExampleContract.kt")
            .withSubResource("contracts/src/main/kotlin/com/example/name/schemas/ExampleSchema.kt")
            .withSubResource("workflows/build.gradle")
            .withSubResource("workflows/src/main/kotlin/com/example/name/workflows/ExampleFlow.kt")
            .build(
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcontract_name=With Cordapp Name",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcontract_cordapp_version=$CONTRACT_CORDAPP_VERSION",
                "-Pworkflow_name=With Cordapp Name",
                "-Pcordapp_workflow_version=$expectedCordappWorkflowVersion",
                "-Pworkflow_cordapp_version=$WORKFLOW_CORDAPP_VERSION"
            )
    }

    @Test
    fun testAllArtifactsPresent() {
        assertThat(testProject.artifacts)
            .anyMatch { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
            .anyMatch { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
            .hasSize(2)
    }

    @Test
    fun verifyContractCPKName() {
        val contractCPK = testProject.artifacts.single { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
        assertThat(contractCPK).isRegularFile
        with(contractCPK.manifest.mainAttributes) {
            assertThat(getValue(CPK_CORDAPP_NAME)).isEqualTo(CONTRACT_CORDAPP_CORDAPPNAME)
        }
    }

    @Test
    fun verifyWorkflowCPKName() {
        val workflowCPK = testProject.artifacts.single { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
        assertThat(workflowCPK).isRegularFile
        with(workflowCPK.manifest.mainAttributes) {
            assertThat(getValue(CPK_CORDAPP_NAME)).isEqualTo(WORKFLOW_CORDAPP_CORDAPPNAME)
        }
    }

}
