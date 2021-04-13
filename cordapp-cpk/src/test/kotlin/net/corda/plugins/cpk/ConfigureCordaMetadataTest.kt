package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.DYNAMICIMPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import java.nio.file.Path

class ConfigureCordaMetadataTest {
    companion object {
        private const val CORDAPP_VERSION = "1.1.1-SNAPSHOT"
        private const val HIBERNATE_ANNOTATIONS_PACKAGE = "org.hibernate.annotations"
        private const val HIBERNATE_PROXY_PACKAGE = "org.hibernate.proxy"
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("configure-corda-metadata")
                .withSubResource("cordapp/src/main/kotlin/com/example/metadata/custom/ExampleContract.kt")
                .withSubResource("cordapp/build.gradle")
                .build(
                    "-Pcontract_name=Configure Contract Metadata",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcordapp_version=$CORDAPP_VERSION"
                )
        }
    }

    @Test
    fun testAllArtifactsPresent() {
        assertThat(testProject.artifacts)
            .anyMatch { it.toString().endsWith("cordapp-$CORDAPP_VERSION.jar") }
            .hasSize(1)
    }

    @Test
    fun verifyCustomMetadata() {
        val contractJar = testProject.artifacts.single { it.toString().endsWith("cordapp-$CORDAPP_VERSION.jar") }
        assertThat(contractJar).isRegularFile()
        with(contractJar.manifest.mainAttributes) {
            assertThat(getValue("Corda-Other-Contract-Classes"))
                .isEqualTo("com.example.metadata.custom.ExampleContract,com.example.metadata.custom.ExampleContract\$NestedContract")
            assertThat(getValue(CORDA_CONTRACT_CLASSES))
                .isEqualTo("com.example.metadata.custom.ExampleContract,com.example.metadata.custom.ExampleContract\$NestedContract")
            assertThatHeader(getValue(IMPORT_PACKAGE))
                .doesNotContainPackage(HIBERNATE_ANNOTATIONS_PACKAGE, HIBERNATE_PROXY_PACKAGE)
            assertThatHeader(getValue(DYNAMICIMPORT_PACKAGE))
                .doesNotContainPackage(HIBERNATE_ANNOTATIONS_PACKAGE, HIBERNATE_PROXY_PACKAGE)
                .containsPackageWithAttributes("org.testing")
                .containsPackageWithAttributes("com.foo.bar")
        }
    }
}
