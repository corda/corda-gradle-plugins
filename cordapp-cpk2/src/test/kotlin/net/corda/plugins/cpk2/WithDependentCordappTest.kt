package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@TestInstance(PER_CLASS)
class WithDependentCordappTest {
    private companion object {
        private const val CORDA_GUAVA_VERSION = "20.0"
        private const val librarySlf4jVersion = "1.7.21"
    }

    private fun buildProject(
        guavaVersion: String,
        libraryGuavaVersion: String,
        testProjectDir: Path,
        reporter: TestReporter
    ): GradleProject {
        return GradleProject(testProjectDir, reporter)
            .withTestName("with-dependent-cordapp")
            .withSubResource("library/build.gradle")
            .withSubResource("cordapp/build.gradle")
            .build(
                "-Pcordapp_workflow_version=$expectedCordappWorkflowVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Plibrary_guava_version=$libraryGuavaVersion",
                "-Pguava_version=$guavaVersion",
                "-Pslf4j_version=$librarySlf4jVersion"
            )
    }

    @ParameterizedTest
    @CsvSource(
        "$CORDA_GUAVA_VERSION,29.0-jre",
        "19.0,$CORDA_GUAVA_VERSION",
        "19.0,19.0",
        "28.2-jre,28.2-jre"
    )
    fun hasCordappDependency(
        guavaVersion: String,
        libraryGuavaVersion: String,
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val testProject = buildProject(guavaVersion, libraryGuavaVersion, testProjectDir, reporter)
        val cordaSlf4jVersion = testProject.properties.getProperty("corda_slf4j_version")
        val bndlibVersion = testProject.properties.getProperty("bndlib_version")
        val osgiVersion = testProject.properties.getProperty("osgi_version")
        val jetbrainsAnnotationsVersion = testProject.properties.getProperty("jetbrains_annotations_version")
        assertEquals(CORDA_GUAVA_VERSION, testProject.properties.getProperty("corda_guava_version"))
        assertNotEquals(cordaSlf4jVersion, librarySlf4jVersion)

        assertThat(testProject.outputLines)
            .contains("COMPILE-WORKFLOW> biz.aQute.bnd.annotation-${bndlibVersion}.jar")
            .contains("COMPILE-WORKFLOW> osgi.annotation-${osgiVersion}.jar")
            .contains("COMPILE-WORKFLOW> annotations-${jetbrainsAnnotationsVersion}.jar")
            .contains("COMPILE-WORKFLOW> guava-${guavaVersion}.jar")
            .contains("COMPILE-WORKFLOW> cordapp.jar")
            .contains("EXTERNAL-WORKFLOW> corda-api-${cordaApiVersion}.jar")
            .contains("EXTERNAL-WORKFLOW> slf4j-api-${cordaSlf4jVersion}.jar")
            .contains("EXTERNAL-WORKFLOW> cordapp.jar")
            .contains("COMPILE-CONTRACT> biz.aQute.bnd.annotation-${bndlibVersion}.jar")
            .contains("COMPILE-CONTRACT> osgi.annotation-${osgiVersion}.jar")
            .contains("COMPILE-CONTRACT> annotations-${jetbrainsAnnotationsVersion}.jar")
            .contains("COMPILE-CONTRACT> slf4j-api-${cordaSlf4jVersion}.jar")
            .contains("COMPILE-CONTRACT> commons-io-${commonsIoVersion}.jar")
            .contains("COMPILE-CONTRACT> library.jar")
            .contains("EXTERNAL-CONTRACT> corda-api-${cordaApiVersion}.jar")
            .contains("EXTERNAL-CONTRACT> slf4j-api-${cordaSlf4jVersion}.jar")
            .noneMatch { it.startsWith("EXTERNAL-WORKFLOW> guava-") }
            .noneMatch { it.startsWith("EXTERNAL-WORKFLOW> ") && it.contains("osgi.") }
            .noneMatch { it.startsWith("EXTERNAL-CONTRACT> guava-") }
            .noneMatch { it.startsWith("EXTERNAL-CONTRACT> ") && it.contains("osgi.") }
            .doesNotContain(
                "EXTERNAL-CONTRACT> library.jar",
                "COMPILE-CONTRACT> guava-${guavaVersion}.jar",
                "COMPILE-CONTRACT> guava-${libraryGuavaVersion}.jar",
                "COMPILE-WORKFLOW> commons-io-${commonsIoVersion}.jar",
                "COMPILE-WORKFLOW> slf4j-api-${librarySlf4jVersion}.jar",
                "COMPILE-WORKFLOW> library.jar"
            )

        assertThat(testProject.libraries)
            .anyMatch { it == "guava-$guavaVersion.jar" }
            .noneMatch { it == "commons-io-$commonsIoVersion.jar" }
            .noneMatch { it == "slf4j-api-${cordaSlf4jVersion}.jar" }
            .noneMatch { it == "slf4j-api-${librarySlf4jVersion}.jar" }
            .noneMatch { it == "biz.aQute.bnd.annotation-${bndlibVersion}.jar" }
            .noneMatch { it == "osgi.annotation-${osgiVersion}.jar" }
            .noneMatch { it == "annotations-${jetbrainsAnnotationsVersion}.jar" }
            .noneMatch { it == "library.jar" }
            .noneMatch { it == "cordapp.jar" }
            .hasSizeGreaterThanOrEqualTo(1)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi("0") }
            .allMatch { it.verifySameSignerAsMe }
            .hasSize(1)

        if (libraryGuavaVersion != guavaVersion) {
            assertThat(testProject.libraries)
                .noneMatch { it == "guava-${libraryGuavaVersion}.jar" }
        }

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val cpk = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cpk).isRegularFile

        val contractCpk = testProject.buildDir.resolve("cordapp.jar")
        assertThat(contractCpk).isRegularFile
        assertThat(listLibrariesForCpk(contractCpk))
            .noneMatch { it == "biz.aQute.bnd.annotation-${bndlibVersion}.jar" }
            .noneMatch { it == "osgi.annotation-${osgiVersion}.jar" }
            .noneMatch { it == "annotations-${jetbrainsAnnotationsVersion}.jar" }
            .noneMatch { it == "slf4j-api-${librarySlf4jVersion}.jar" }
            .noneMatch { it == "slf4j-api-${cordaSlf4jVersion}.jar" }
            .anyMatch { it == "guava-${libraryGuavaVersion}.jar" }
            .anyMatch { it == "commons-io-$commonsIoVersion.jar" }
            .anyMatch { it == "library.jar" }
    }
}
