package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
import java.util.jar.JarFile

class VerifyCordappDependencyTest {
    companion object {
        private const val cordappVersion = "1.1.1-SNAPSHOT"
        private const val hostVersion = "2.0.0-SNAPSHOT"

        private const val kotlinOsgiVersion = "version=\"[1.3,2)\""
        private const val cordaOsgiVersion = "version=\"[5.0,6)\""
        private const val cordappOsgiVersion = "version=\"[1.1,2)\""
        private const val hostOsgiVersion = "version=\"2.0.0\""

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("verify-cordapp-dependency")
                .withSubResource("src/main/kotlin/com/example/host/HostCordapp.kt")
                .withSubResource("cordapp/build.gradle")
                .withSubResource("cordapp/src/main/kotlin/com/example/cordapp/ExampleCordapp.kt")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Phost_version=$hostVersion"
                )
        }
    }

    @Test
    fun verifyCordappDependency() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("cordapp-$cordappVersion.jar") }
            .anyMatch { it.startsWith("commons-io-$commonsIoVersion.jar") }
            .hasSize(2)
        assertThat(testProject.outcomeOf("verifyBundle")).isEqualTo(SUCCESS)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = JarFile(cordapp.toFile()).use(JarFile::getManifest)
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Verify CorDapp Dependency", getValue(BUNDLE_NAME))
            assertEquals("com.example.verify-cordapp-dependency", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals("2.0.0.SNAPSHOT", getValue(BUNDLE_VERSION))
            assertEquals("com.example.cordapp;$cordappOsgiVersion,kotlin;$kotlinOsgiVersion,kotlin.jvm.internal;$kotlinOsgiVersion,net.corda.core.contracts;$cordaOsgiVersion,net.corda.core.transactions;$cordaOsgiVersion", getValue(IMPORT_PACKAGE))
            assertEquals("com.example.host;uses:=\"kotlin,net.corda.core.contracts,net.corda.core.transactions\";$hostOsgiVersion", getValue(EXPORT_PACKAGE))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}