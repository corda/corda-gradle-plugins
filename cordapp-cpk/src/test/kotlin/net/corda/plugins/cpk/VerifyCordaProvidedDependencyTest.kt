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

class VerifyCordaProvidedDependencyTest {
    companion object {
        private const val cordappVersion = "1.0.9.SNAPSHOT"
        private const val cordappOsgiVersion = "version=\"1.0.9\""
        private const val annotationsVersion = "version=\"[1.0,2)\""

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("verify-corda-provided")
                .withSubResource("src/main/java/com/example/provided/Host.java")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion"
                )
        }
    }

    @Test
    fun verifyCordaProvidedDependency() {
        assertThat(testProject.dependencyConstraints).isEmpty()
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
            assertEquals("Verify Corda Provided", getValue(BUNDLE_NAME))
            assertEquals("com.example.verify-corda-provided", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals("1.0.9.SNAPSHOT", getValue(BUNDLE_VERSION))
            assertEquals("com.example.annotations;$annotationsVersion", getValue(IMPORT_PACKAGE))
            assertEquals("com.example.provided;uses:=\"com.example.annotations\";$cordappOsgiVersion", getValue(EXPORT_PACKAGE))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}