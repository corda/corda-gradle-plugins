package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
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

class ComplexPackagesTest {
    companion object {
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("complex-packages")
                .withSubResource("src/main/java/com/example/cordapp/package-info.java")
                .withSubResource("src/main/java/com/example/cordapp/sub1/Secret.java")
                .withSubResource("src/main/java/com/example/cordapp/sub2/package-info.java")
                .build("-Pcordapp_contract_version=$expectedCordappContractVersion")
        }
    }

    @Test
    fun complexPackageTest() {
        // We need the OSGi @Export annotation, but must ignore its dependency.
        assertThat(testProject.dependencyConstraints).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = JarFile(cordapp.toFile()).use(JarFile::getManifest)
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Complex Packages", getValue(BUNDLE_NAME))
            assertEquals("com.example.complex-packages", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals("1.0.1.SNAPSHOT", getValue(BUNDLE_VERSION))
            assertNull(getValue(IMPORT_PACKAGE))
            assertEquals("com.example.cordapp;version=\"1.0.1\",com.example.cordapp.sub2;version=\"1.0.1\"", getValue(EXPORT_PACKAGE))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))
            assertEquals("com.example.cordapp.sub1", getValue("Private-Package"))
        }
    }
}