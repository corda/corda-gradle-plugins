package net.corda.plugins.cpk

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
class SimpleCordappTest {
    private companion object {
        private const val SIGNING_TAG = "Jar signing with following options:"
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val ioOsgiVersion = "version=\"[1.4,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"1.0.1\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("simple-cordapp")
            .withSubResource("src/main/java/com/example/contract/ExampleContract.java")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion"
            )
    }

    @Test
    fun simpleTest() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .allMatch { it.hash.isSHA256 }
            .hasSize(1)
        assertThat(testProject.cpkDependencies).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile
        assertThat(cordapp.hashOfEntry(CPK_DEPENDENCIES))
            .isEqualTo(testProject.cpkDependenciesHash)
        assertThat(cordapp.hashOfEntry(DEPENDENCY_CONSTRAINTS))
            .isEqualTo(testProject.dependencyConstraintsHash)

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals(testPlatformVersion, getValue(CORDAPP_PLATFORM_VERSION))
            assertEquals("Simple Java", getValue(BUNDLE_NAME))
            assertEquals("com.example.simple-cordapp", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "org.apache.commons.io;$ioOsgiVersion"
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

    @Test
    fun `test signing passwords are not logged`() {
        assertThat(testProject.outputLines).anyMatch { line ->
            line.startsWith(SIGNING_TAG)
        }.noneMatch { line ->
            line.startsWith(SIGNING_TAG)
                && (line.matches("^.* keypass=[^*,]+,.*\$".toRegex()) || line.matches("^.* storepass=[^*,]+,.*\$".toRegex()))
        }
    }
}
