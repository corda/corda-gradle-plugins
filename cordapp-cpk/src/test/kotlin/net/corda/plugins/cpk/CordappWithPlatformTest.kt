package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Files
import java.nio.file.Path

class CordappWithPlatformTest {
    companion object {
        private const val kotlinOsgiVersion = "version=\"[1.4,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""

        private const val cordappVersion = "3.2.1-SNAPSHOT"

        private lateinit var publisherProject: GradleProject
        private lateinit var testProject: GradleProject

        @BeforeAll
        @JvmStatic
        fun setup(
            @TempDir publisherProjectDir: Path,
            @TempDir testProjectDir: Path,
            reporter: TestReporter
        ) {
            val repositoryDir = Files.createDirectory(testProjectDir.resolve("maven"))
            publisherProject = GradleProject(publisherProjectDir, reporter)
                .withTestName("publish-platform")
                .withTaskName("publishAllPublicationsToTestRepository")
                .build(
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Prepository_dir=$repositoryDir"
                )
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("cordapp-with-platform")
                .withSubResource("src/main/kotlin/com/example/cpk/platform/ExampleContract.kt")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcorda_api_version=$cordaApiVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Prepository_dir=$repositoryDir"
                )
        }
    }

    @Test
    fun testCordappWithPlatform() {
        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals(testPlatformVersion, getValue(CORDAPP_PLATFORM_VERSION))
            assertEquals("CorDapp With Platform", getValue(BUNDLE_NAME))
            assertEquals("com.example.cordapp-with-platform", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "kotlin;$kotlinOsgiVersion",
                "kotlin.jvm.internal;$kotlinOsgiVersion",
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsPackageWithAttributes(
                "com.example.cpk.platform",
                "uses:=kotlin,net.corda.v5.ledger.contracts,net.corda.v5.ledger.transactions",
                "version=${osgiVersion(cordappVersion).withoutQualifier}"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
        }
    }
}
