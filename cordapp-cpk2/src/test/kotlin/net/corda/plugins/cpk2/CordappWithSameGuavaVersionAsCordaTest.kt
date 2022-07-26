package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
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
class CordappWithSameGuavaVersionAsCordaTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val cordaGuavaVersion = "20.0"

        private const val guavaOsgiVersion = "version=\"[20.0,21)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"1.0.1\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-with-guava")
            .withSubResource("src/main/java/com/example/contract/GuavaContract.java")
            .build(
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_guava_version=$cordaGuavaVersion",
                "-Pguava_version=$cordaGuavaVersion",
                "-Pcordapp_version=$cordappVersion"
            )
    }

    @Test
    fun sameGuavaVersionsTest() {
        assertThat(testProject.libraries)
            .anyMatch { it == "guava-$cordaGuavaVersion.jar" }
            .hasSizeGreaterThanOrEqualTo(1)
        assertThat(testProject.cpkDependencies).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("CorDapp With Guava", getValue(BUNDLE_NAME))
            assertEquals("com.example.cordapp-with-guava", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "com.google.common.collect;$guavaOsgiVersion",
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.contract;uses:=\"net.corda.v5.ledger.contracts,net.corda.v5.ledger.transactions\";$cordappOsgiVersion"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}
