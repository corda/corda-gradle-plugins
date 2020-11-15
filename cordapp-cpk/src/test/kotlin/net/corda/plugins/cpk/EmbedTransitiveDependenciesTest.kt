package net.corda.plugins.cpk

import aQute.bnd.osgi.Constants.PRIVATE_PACKAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_CLASSPATH
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

class EmbedTransitiveDependenciesTest {
    companion object {
        private const val cordappVersion = "2.3.4-SNAPSHOT"
        private const val annotationsOsgiVersion = "version=\"$annotationsVersion\""

        private lateinit var testProject: GradleProject

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withTestName("embed-transitive-deps")
                .withSubResource("library/build.gradle")
                .build(
                    "-Pcordapp_contract_version=$expectedCordappContractVersion",
                    "-Pannotations_version=$annotationsVersion",
                    "-Pcommons_io_version=$commonsIoVersion",
                    "-Pcordapp_version=$cordappVersion"
                )
        }
    }

    @Test
    fun testCordappWithEmbeddedTransitiveDependencies() {
        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.startsWith("library.jar") }
            .anyMatch { it.startsWith("commons-io-$commonsIoVersion.jar") }
            .hasSize(2)

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val libs = JarFile(cordapp.toFile()).use { jar ->
            jar.entries().asSequence()
                .map(JarEntry::getName)
                .filter { it.matches("^lib/.*\\.jar\$".toRegex()) }
                .toList()
        }
        assertThat(libs)
            .contains("lib/annotations-$annotationsVersion.jar")
            .hasSize(1)

        val jarManifest = JarFile(cordapp.toFile()).use(JarFile::getManifest)
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertEquals("Embed Transitive Dependencies", getValue(BUNDLE_NAME))
            assertEquals("com.example.embed-transitive-deps", getValue(BUNDLE_SYMBOLICNAME))
            assertEquals("2.3.4.SNAPSHOT", getValue(BUNDLE_VERSION))
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"", getValue(REQUIRE_CAPABILITY))
            assertEquals("com.example.annotations;$annotationsOsgiVersion", getValue(EXPORT_PACKAGE))
            assertEquals("lib", getValue(PRIVATE_PACKAGE))
            assertEquals("true", getValue("Sealed"))

            assertThat(getValue(BUNDLE_CLASSPATH))
                .startsWith(".,")
                .contains(libs.map { ",$it" })
        }
    }
}
