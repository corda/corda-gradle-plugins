package net.corda.plugins.cpk

import java.io.StringReader
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.IMPORT_PACKAGE

/**
 * Our library is an OSGi bundle, and so Gradle must compile
 * our CorDapp against library.jar. It MUST NOT use library's
 * unpackaged classes, which have no OSGi metadata.
 */
@TestInstance(PER_CLASS)
class CordappClassesDependencyTest {
    private companion object {
        private const val CORDAPP_VERSION = "1.1.1-SNAPSHOT"
        private const val LIBRARY_VERSION = "2.2.2-SNAPSHOT"
        private const val compilePrefix = "COMPILE "
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-classes-dep")
            .withSubResource("src/main/kotlin/com/example/host/CordappHostContract.kt")
            .withSubResource("library/src/main/java/com/example/library/package-info.java")
            .withSubResource("library/src/main/java/com/example/library/ExternalLibrary.java")
            .withSubResource("library/src/main/java/com/example/library/impl/ExternalLibraryImpl.java")
            .withSubResource("library/build.gradle")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$CORDAPP_VERSION",
                "-Plibrary_version=$LIBRARY_VERSION"
            )
    }

    @Test
    fun testCordappWithLibraryDependency() {
        val compileDeps = StringReader(testProject.output)
            .readLines()
            .filter { it.startsWith(compilePrefix) }
            .map { it.removePrefix(compilePrefix) }
        assertThat(compileDeps)
            .contains("library-$LIBRARY_VERSION.jar")

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertThatHeader(getValue(IMPORT_PACKAGE))
                .containsPackageWithAttributes("com.example.library", "version=${toOSGiRange(LIBRARY_VERSION)}")
        }
    }
}
