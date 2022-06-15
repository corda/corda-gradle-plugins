package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Verify that transitive cordapp and cordaProvided dependencies
 * are inherited by downstream CPK projects.
 */
@TestInstance(PER_CLASS)
class TransitiveRemoteCordappsTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpk2Version = "2.0-SNAPSHOT"
        private const val cpk3Version = "3.0-SNAPSHOT"
        private const val cpk1Type = "foo"
        private const val cpk2Type = "api"

        private const val ioOsgiVersion = "version=\"[1.4,2)\""
        private const val kotlinOsgiVersion = "version=\"[1.7,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"1.0.1\""
    }

    private lateinit var publisherProject: GradleProject
    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(
        @TempDir publisherProjectDir: Path,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val repositoryDir = Files.createDirectory(testProjectDir.resolve("maven"))
        publisherProject = GradleProject(publisherProjectDir, reporter)
            .withTestName("transitive-cordapps")
            .withSubResource("src/main/kotlin/com/example/transitives/ExampleContract.kt")
            .withSubResource("cpk-one/build.gradle")
            .withSubResource("cpk-two/build.gradle")
            .withSubResource("cpk-three/build.gradle")
            .withTaskName("publishAllPublicationsToTestRepository")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcpk1_version=$cpk1Version",
                "-Pcpk2_version=$cpk2Version",
                "-Pcpk3_version=$cpk3Version",
                "-Prepository_dir=$repositoryDir",
                "-Pcpk1_type=$cpk1Type",
                "-Pcpk2_type=$cpk2Type"
            )

        // Check that we could still read all of Gradle's MavenPom properties.
        assertThat(publisherProject.outputLines)
            .noneMatch { it.startsWith("INTERNAL API:") }

        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("transitive-remote-cordapps")
            .withSubResource("src/main/kotlin/com/example/transitives/ExampleContract.kt")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcpk3_version=$cpk3Version",
                "-Prepository_dir=$repositoryDir"
            )
    }

    @Test
    fun remoteTransitivesTest() {
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cpk-one" && it.version == toOSGi(cpk1Version) && it.type == cpk1Type }
            .anyMatch { it.name == "com.example.cpk-two" && it.version == toOSGi(cpk2Version) && it.type == cpk2Type }
            .anyMatch { it.name == "com.example.cpk-three" && it.version == toOSGi(cpk3Version) && it.type == null }
            .allMatch { it.signers.allSHA256 }
            .hasSize(3)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile
        assertThat(cordapp.hashOfEntry(CPK_DEPENDENCIES))
            .isEqualTo(testProject.cpkDependenciesHash)

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Transitive Remote CorDapps", getValue(BUNDLE_NAME))
            assertEquals("com.example.transitive-remote-cordapps", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertThatHeader(getValue(IMPORT_PACKAGE)).containsAll(
                "kotlin;$kotlinOsgiVersion",
                "kotlin.io;$kotlinOsgiVersion",
                "kotlin.jvm.internal;$kotlinOsgiVersion",
                "kotlin.text;$kotlinOsgiVersion",
                "net.corda.v5.ledger.contracts;$cordaOsgiVersion",
                "net.corda.v5.ledger.transactions;$cordaOsgiVersion",
                "org.apache.commons.io;$ioOsgiVersion"
            )
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.transitives;uses:=\"kotlin,net.corda.v5.ledger.contracts,net.corda.v5.ledger.transactions\";$cordappOsgiVersion"
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}
