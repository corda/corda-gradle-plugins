package net.corda.plugins.cpk

import aQute.bnd.osgi.Constants.PRIVATE_PACKAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

class WithEmbeddedJarTest {
    companion object {
        private const val cordappVersion = "1.0.2-SNAPSHOT"
        private const val guavaVersion = "29.0-jre"
        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("with-embedded-jar")
                .withSubResource("library/build.gradle")
                .build(
                    "-Pcordapp_version=$cordappVersion",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pguava_version=$guavaVersion"
                )
        }
    }

    @Test
    fun hasEmbeddedJar() {
        assertThat(testProject.dependencyConstraints).isEmpty()
        assertThat(testProject.cpkDependencies).isEmpty()

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        val libs = JarFile(cordapp.toFile()).use { jar ->
            jar.entries().asSequence()
                .map(JarEntry::getName)
                .filter { it.matches("^lib/.*\\.jar\$".toRegex()) }
                .toList()
        }
        assertThat(libs)
            .contains("lib/library.jar", "lib/guava-$guavaVersion.jar")
            .hasSizeGreaterThan(2)

        with(jarManifest.mainAttributes) {
            assertEquals("With Embedded Jar", getValue(BUNDLE_NAME))
            assertEquals("com.example.with-embedded-jar", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals(toOSGi(cordappVersion), getValue(BUNDLE_VERSION))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("Test-Licence", getValue(BUNDLE_LICENSE))
            assertEquals("R3", getValue(BUNDLE_VENDOR))
            assertEquals("true", getValue("Sealed"))

            assertThat(getValue(PRIVATE_PACKAGE)).isNotNull()
            assertThat(getValue(IMPORT_PACKAGE)).isNotNull()
            assertThat(getValue(EXPORT_PACKAGE)).isNull()
            assertThat(getValue(BUNDLE_CLASSPATH))
                .startsWith(".,")
                .contains(libs.map { ",$it" })
         }
    }
}
