package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path

@TestInstance(PER_CLASS)
class VerifyCordappDependencyTest {
    private companion object {
        private const val cordappVersion = "1.1.1-SNAPSHOT"
        private const val hostVersion = "2.0.0-SNAPSHOT"

        private const val kotlinOsgiVersion = "version=\"[1.4,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"[1.1,2)\""
        private const val hostOsgiVersion = "version=\"2.0.0\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("verify-cordapp-dependency")
            .withSubResource("src/main/kotlin/com/example/host/HostCordapp.kt")
            .withSubResource("cordapp/build.gradle")
            .withSubResource("cordapp/src/main/kotlin/com/example/cordapp/ExampleCordapp.kt")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_collections_version=$commonsCollectionsVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Phost_version=$hostVersion"
            )
    }

    @Test
    fun verifyCordappDependency() {
        assertThat(testProject.dependencyConstraints)
            .noneMatch { it.fileName == "cordapp-$cordappVersion.jar" }
            .anyMatch { it.fileName == "commons-collections-$commonsCollectionsVersion.jar" }
            .allMatch { it.hash.isSHA256 }
            .hasSize(1)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .allMatch { it.signers.isSameAsMe }
            .hasSize(1)
        assertThat(testProject.outcomeOf("verifyBundle")).isEqualTo(SUCCESS)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Verify CorDapp Dependency", getValue(BUNDLE_NAME))
            assertEquals("com.example.verify-cordapp-dependency", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(hostVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "com.example.cordapp;$cordappOsgiVersion",
                "kotlin;$kotlinOsgiVersion",
                "kotlin.jvm.internal;$kotlinOsgiVersion",
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.host;uses:=\"kotlin,net.corda.v5.ledger.contracts,net.corda.v5.ledger.transactions\";$hostOsgiVersion"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}
