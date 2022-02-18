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
class VerifyCompileOnlyDependencyTest {
    private companion object {
        private const val cordappVersion = "1.0.5.SNAPSHOT"
        private const val cordappOsgiVersion = "version=\"1.0.5\""
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("verify-compile-only")
            .withSubResource("src/main/java/com/example/sample/Host.java")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pannotations_version=$annotationsVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcordapp_version=$cordappVersion"
            )
    }

    @Test
    fun verifyCompileOnlyDependency() {
        assertThat(testProject.dependencyConstraints).isEmpty()
        assertThat(testProject.cpkDependencies).isEmpty()
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
            assertEquals("Verify Compile Only", getValue(BUNDLE_NAME))
            assertEquals("com.example.verify-compile-only", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertEquals("java.lang", getValue(IMPORT_PACKAGE))
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll("com.example.sample;$cordappOsgiVersion")
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertNull(getValue("Private-Package"))
        }
    }
}
