package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
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
class ComplexPackagesTest {
    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("complex-packages")
            .withSubResource("src/main/java/com/example/cordapp/package-info.java")
            .withSubResource("src/main/java/com/example/cordapp/sub1/Secret.java")
            .withSubResource("src/main/java/com/example/cordapp/sub2/package-info.java")
            .build("-Pcordapp_contract_version=$expectedCordappContractVersion")
    }

    @Test
    fun complexPackageTest() {
        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Complex Packages", getValue(BUNDLE_NAME))
            assertEquals("com.example.complex-packages", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals("1.0.1.SNAPSHOT", getValue(BUNDLE_VERSION))
            assertEquals("java.lang", getValue(IMPORT_PACKAGE))
            assertThatHeader(getValue(EXPORT_PACKAGE)).containsAll(
                "com.example.cordapp;version=\"1.0.1\"",
                "com.example.cordapp.sub2;version=\"1.0.1\""
            )
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertEquals("com.example.cordapp.sub1", getValue("Private-Package"))
        }
    }
}
