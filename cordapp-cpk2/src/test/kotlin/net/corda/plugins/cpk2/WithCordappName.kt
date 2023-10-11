package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONTRACT_NAME
import net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONTRACT_VERSION
import net.corda.plugins.cpk2.CordappUtils.CORDAPP_PLATFORM_VERSION
import net.corda.plugins.cpk2.CordappUtils.CORDAPP_WORKFLOW_NAME
import net.corda.plugins.cpk2.CordappUtils.CORDAPP_WORKFLOW_VERSION
import net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_LICENCE
import net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_NAME
import net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_VENDOR
import net.corda.plugins.cpk2.CordappUtils.CPK_CORDAPP_VERSION
import net.corda.plugins.cpk2.CordappUtils.CPK_FORMAT
import net.corda.plugins.cpk2.CordappUtils.CPK_FORMAT_TAG
import net.corda.plugins.cpk2.CordappUtils.CPK_PLATFORM_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.DYNAMICIMPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.nio.file.Path

@TestInstance(PER_CLASS)
class WithCordappName {
    private companion object {
        private const val CONTRACT_CORDAPP_NAME = "com.example.contracts"
        private const val CONTRACT_CORDAPP_VERSION = "1.1.1-SNAPSHOT"
        private const val CONTRACT_CORDAPP_CORDAPPNAME = "com.example.contracts.cpk"
        private const val WORKFLOW_CORDAPP_NAME = "com.example.workflows"
        private const val WORKFLOW_CORDAPP_VERSION = "2.2.2-SNAPSHOT"
        private const val WORKFLOW_CORDAPP_CORDAPPNAME = "com.example.workflows.cpk"
        private const val HIBERNATE_ANNOTATIONS_PACKAGE = "org.hibernate.annotations"
        private const val HIBERNATE_PROXY_PACKAGE = "org.hibernate.proxy"
        private const val JAVAX_PERSISTENCE_PACKAGE = "javax.persistence"
        private const val TEST_LICENCE = "Test-Licence"
        private const val TEST_VENDOR = "R3"
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
    fun verifyContractMetadata() {
        val persistenceApiVersion = testProject.properties.getProperty("persistence_api_version")
        val contractJar = testProject.artifacts.single { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
        assertThat(contractJar).isRegularFile
        with(contractJar.manifest.mainAttributes) {
            assertThat(getValue(CORDAPP_PLATFORM_VERSION))
                .isEqualTo(testPlatformVersion)
            assertThat(getValue(CORDA_CONTRACT_CLASSES))
                .isEqualTo("com.example.name.contracts.ExampleContract,com.example.name.contracts.ExampleContract\$NestedContract")
            assertThat(getValue(CORDA_MAPPED_SCHEMA_CLASSES))
                .isEqualTo("com.example.name.schemas.ExampleSchemaV1")
            assertThat(getValue(CORDA_WORKFLOW_CLASSES)).isNull()
            assertThat(getValue(CORDAPP_CONTRACT_NAME))
                .isEqualTo("With Cordapp Name")
            assertThat(getValue(CORDAPP_CONTRACT_VERSION))
                .isEqualTo(expectedCordappContractVersion.toString())
            assertThat(getValue(BUNDLE_SYMBOLICNAME))
                .isEqualTo(CONTRACT_CORDAPP_NAME)
            assertThat(getValue(BUNDLE_VERSION))
                .isEqualTo(toOSGi(CONTRACT_CORDAPP_VERSION))
            assertThat(getValue(BUNDLE_LICENSE))
                .isEqualTo(TEST_LICENCE)
            assertThat(getValue(BUNDLE_VENDOR))
                .isEqualTo(TEST_VENDOR)
            assertThatHeader(getValue(IMPORT_PACKAGE))
                .doesNotContainPackage(HIBERNATE_ANNOTATIONS_PACKAGE, HIBERNATE_PROXY_PACKAGE)
                .containsPackageWithAttributes(JAVAX_PERSISTENCE_PACKAGE, "version=${toOSGiRange(persistenceApiVersion)}")
            assertThatHeader(getValue(DYNAMICIMPORT_PACKAGE))
                .containsPackageWithAttributes(HIBERNATE_ANNOTATIONS_PACKAGE)
                .containsPackageWithAttributes(HIBERNATE_PROXY_PACKAGE)
        }
    }

    @Test
    fun verifyContractCPKMetadata() {
        val contractCPK = testProject.artifacts.single { it.toString().endsWith("contracts-$CONTRACT_CORDAPP_VERSION.jar") }
        assertThat(contractCPK).isRegularFile
        with(contractCPK.manifest.mainAttributes) {
            assertThat(getValue(CPK_FORMAT_TAG)).isEqualTo(CPK_FORMAT)
            assertThat(getValue(CPK_CORDAPP_NAME)).isEqualTo(CONTRACT_CORDAPP_CORDAPPNAME)
            assertThat(getValue(CPK_CORDAPP_VERSION)).isEqualTo(toOSGi(CONTRACT_CORDAPP_VERSION))
            assertThat(getValue(CPK_CORDAPP_LICENCE)).isEqualTo(TEST_LICENCE)
            assertThat(getValue(CPK_CORDAPP_VENDOR)).isEqualTo(TEST_VENDOR)
            assertThat(getValue(CPK_PLATFORM_VERSION)).isEqualTo(testPlatformVersion)
        }
    }

    @Test
    fun verifyWorkflowMetadata() {
        val workflowJar = testProject.artifacts.single { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
        assertThat(workflowJar).isRegularFile
        with(workflowJar.manifest.mainAttributes) {
            assertThat(getValue(CORDAPP_PLATFORM_VERSION))
                .isEqualTo(testPlatformVersion)
            assertThat(getValue(CORDA_WORKFLOW_CLASSES))
                .isEqualTo("com.example.name.workflows.ExampleFlow,com.example.name.workflows.ExampleFlow\$NestedFlow")
            assertThat(getValue(CORDA_CONTRACT_CLASSES)).isNull()
            assertThat(getValue(CORDA_MAPPED_SCHEMA_CLASSES)).isNull()
            assertThat(getValue(CORDAPP_WORKFLOW_NAME))
                .isEqualTo("With Cordapp Name")
            assertThat(getValue(CORDAPP_WORKFLOW_VERSION))
                .isEqualTo(expectedCordappWorkflowVersion.toString())
            assertThat(getValue(BUNDLE_SYMBOLICNAME))
                .isEqualTo(WORKFLOW_CORDAPP_NAME)
            assertThat(getValue(BUNDLE_VERSION))
                .isEqualTo(toOSGi(WORKFLOW_CORDAPP_VERSION))
            assertThat(getValue(BUNDLE_LICENSE))
                .isEqualTo(TEST_LICENCE)
            assertThat(getValue(BUNDLE_VENDOR))
                .isEqualTo(TEST_VENDOR)
            assertThatHeader(getValue(IMPORT_PACKAGE))
                .doesNotContainPackage(HIBERNATE_ANNOTATIONS_PACKAGE, HIBERNATE_PROXY_PACKAGE)
            assertThatHeader(getValue(DYNAMICIMPORT_PACKAGE))
                .containsPackageWithAttributes(HIBERNATE_ANNOTATIONS_PACKAGE)
                .containsPackageWithAttributes(HIBERNATE_PROXY_PACKAGE)
        }
    }

    @Test
    fun verifyWorkflowCPKMetadata() {
        val workflowCPK = testProject.artifacts.single { it.toString().endsWith("workflows-$WORKFLOW_CORDAPP_VERSION.jar") }
        assertThat(workflowCPK).isRegularFile
        with(workflowCPK.manifest.mainAttributes) {
            assertThat(getValue(CPK_FORMAT_TAG)).isEqualTo(CPK_FORMAT)
            assertThat(getValue(CPK_CORDAPP_NAME)).isEqualTo(WORKFLOW_CORDAPP_CORDAPPNAME)
            assertThat(getValue(CPK_CORDAPP_VERSION)).isEqualTo(toOSGi(WORKFLOW_CORDAPP_VERSION))
            assertThat(getValue(CPK_CORDAPP_LICENCE)).isEqualTo(TEST_LICENCE)
            assertThat(getValue(CPK_CORDAPP_VENDOR)).isEqualTo(TEST_VENDOR)
            assertThat(getValue(CPK_PLATFORM_VERSION)).isEqualTo(testPlatformVersion)
        }
    }

}
