package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CordappApiLibraryTest {
    private companion object {
        private const val CONTRACT_CORDAPP_VERSION = "1.1.5-SNAPSHOT"
        private const val WORKFLOW_CORDAPP_VERSION = "2.6.3-SNAPSHOT"
        private const val expectedContractGuavaVersion = "29.0-jre"
        private const val expectedWorkflowGuavaVersion = "19.0"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        // Check that the Workflow CorDapp is using a lower version
        // of Guava than the Contract CorDapp.
        assertThat(osgiVersion(expectedWorkflowGuavaVersion))
            .isLessThan(osgiVersion(expectedContractGuavaVersion))

        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-api-library")
            .withSubResource("src/main/kotlin/com/example/workflow/ExampleWorkflow.kt")
            .withSubResource("cordapp/build.gradle")
            .build(
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcordapp_workflow_version=$expectedCordappWorkflowVersion",
                "-Pcontract_cordapp_version=$CONTRACT_CORDAPP_VERSION",
                "-Pcontract_guava_version=$expectedContractGuavaVersion",
                "-Pworkflow_cordapp_version=$WORKFLOW_CORDAPP_VERSION",
                "-Pworkflow_guava_version=$expectedWorkflowGuavaVersion"
            )
    }

    @Test
    fun hasCordappApiLibrary() {
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(CONTRACT_CORDAPP_VERSION) }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Cordapp API Library", getValue(CORDAPP_WORKFLOW_NAME))
            assertEquals("Cordapp API Library", getValue(BUNDLE_NAME))
            assertEquals("com.example.cordapp-api-library", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(WORKFLOW_CORDAPP_VERSION), getValue(BUNDLE_VERSION))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))

            assertThatHeader(getValue(IMPORT_PACKAGE)).containsPackageWithAttributes(
                "com.google.common.collect", "version=${toOSGiRange(expectedWorkflowGuavaVersion)}"
            )
        }
    }
}
