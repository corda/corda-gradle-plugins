package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.CordappUtils.CORDAPP_PLATFORM_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.nio.file.Path

@TestInstance(PER_CLASS)
class WithProvidedLibraryTest {
    private companion object {
        private const val cordappVersion = "1.0.2-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("with-provided-library")
            .withSubResource("library/build.gradle")
            .build(
                "-Pcordapp_version=$cordappVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion"
            )
    }

    @Test
    fun hasProvidedLibrary() {
        assertThat(testProject.libraries).isEmpty()
        assertThat(testProject.cpkDependencies).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("1500", getValue(CORDAPP_PLATFORM_VERSION))
            assertEquals("With Provided Library", getValue(BUNDLE_NAME))
            assertEquals("com.example.with-provided-library", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
        }
    }
}
