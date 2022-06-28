package net.corda.plugins.cpk

import aQute.bnd.osgi.Constants.PRIVATE_PACKAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

@TestInstance(PER_CLASS)
class CordappWithEmbeddedTransitivesTest {
    private companion object {
        private const val cordappVersion = "2.3.4-SNAPSHOT"
        private const val hostVersion = "1.2.3-SNAPSHOT"
        private const val slf4jVersion = "2.0.0-alpha1"

        private const val kotlinOsgiVersion = "version=\"[1.4,2)\""
        private const val commonsIoOsgiVersion = "version=\"[1.4,2)\""
        private const val slf4jOsgiVersion = "version=\"[2.0,3)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"[2.3,3)\""
        private const val hostOsgiVersion = "version=\"1.2.3\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-embedded-transitives")
            .withSubResource("src/main/kotlin/com/example/host/HostContract.kt")
            .withSubResource("cordapp/build.gradle")
            .withSubResource("cordapp/src/main/kotlin/com/example/cordapp/CordappContract.kt")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_collections_version=$commonsCollectionsVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pslf4j_version=$slf4jVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Phost_version=$hostVersion"
            )
    }

    @Test
    fun testCordappWithEmbeddedTransitiveDependencies() {
        assertThat(testProject.dependencyConstraints)
            .noneMatch { it.fileName == "cordapp-$cordappVersion.jar" }
            .anyMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .hasSize(1)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi(cordappVersion) }
            .hasSize(1)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val libs = JarFile(cordapp.toFile()).use { jar ->
            jar.entries().asSequence()
                .map(JarEntry::getName)
                .filter { it.matches("^lib/.*\\.jar\$".toRegex()) }
                .toList()
        }
        assertThat(libs)
            .contains("lib/embeddable-library.jar")
            .contains("lib/commons-collections-$commonsCollectionsVersion.jar")
            .hasSize(2)

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("CorDapp Embedded Transitives", getValue(BUNDLE_NAME))
            assertEquals("com.example.cordapp-embedded-transitives", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(hostVersion), getValue(BUNDLE_VERSION))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("true", getValue("Sealed"))

            assertThat(getValue(PRIVATE_PACKAGE)?.split(","))
                .contains("lib", "com.example.embeddable", "org.apache.commons.collections")
                .hasSizeGreaterThanOrEqualTo(3)
            assertThat(getValue(BUNDLE_CLASSPATH))
                .startsWith(".,")
                .contains(libs.map { ",$it" })
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "com.example.cordapp;$cordappOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion",
                "org.apache.commons.io;$commonsIoOsgiVersion",
                "kotlin;$kotlinOsgiVersion",
                "org.slf4j;$slf4jOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.host;uses:=\"com.example.cordapp,kotlin,net.corda.v5.ledger.transactions\";$hostOsgiVersion"
            )
        }
    }
}
