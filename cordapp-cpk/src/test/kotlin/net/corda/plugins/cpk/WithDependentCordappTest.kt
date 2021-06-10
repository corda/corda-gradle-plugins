package net.corda.plugins.cpk

import net.corda.plugins.cpk.xml.loadDependencyConstraints
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class WithDependentCordappTest {
    companion object {
        private const val CORDA_GUAVA_VERSION = "20.0"
        private const val librarySlf4jVersion = "1.7.21"

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
        val bndVersion = testProject.properties.getProperty("bnd_version")
        assertEquals(CORDA_GUAVA_VERSION, testProject.properties.getProperty("corda_guava_version"))
        assertNotEquals(cordaSlf4jVersion, librarySlf4jVersion)

        assertThat(testProject.output.split(System.lineSeparator()))
            .contains("COMPILE-WORKFLOW> biz.aQute.bnd.annotation-${bndVersion}.jar")
            .contains("COMPILE-WORKFLOW> guava-${guavaVersion}.jar")
            .contains("COMPILE-WORKFLOW> cordapp.jar")
            .contains("EXTERNAL-WORKFLOW> corda-api-${cordaApiVersion}.jar")
            .contains("EXTERNAL-WORKFLOW> slf4j-api-${cordaSlf4jVersion}.jar")
            .contains("EXTERNAL-WORKFLOW> cordapp.jar")
            .contains("COMPILE-CONTRACT> biz.aQute.bnd.annotation-${bndVersion}.jar")
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

        assertThat(testProject.dependencyConstraints)
            .anyMatch { it.fileName == "guava-$guavaVersion.jar" }
            .noneMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .noneMatch { it.fileName == "slf4j-api-${cordaSlf4jVersion}.jar" }
            .noneMatch { it.fileName == "slf4j-api-${librarySlf4jVersion}.jar" }
            .noneMatch { it.fileName == "biz.aQute.bnd.annotation-${bndVersion}.jar" }
            .noneMatch { it.fileName == "library.jar" }
            .noneMatch { it.fileName == "cordapp.jar" }
            .allMatch { it.hash.isSHA256 }
            .hasSizeGreaterThanOrEqualTo(1)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cordapp" && it.version == toOSGi("0") }
            .allMatch { it.signers.allSHA256 }
            .hasSize(1)

        if (libraryGuavaVersion != guavaVersion) {
            assertThat(testProject.dependencyConstraints)
                .noneMatch { it.fileName == "guava-${libraryGuavaVersion}.jar" }
        }

        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile()

        val cpk = artifacts.single { it.toString().endsWith(".cpk") }
        assertThat(cpk).isRegularFile()

        val cordappDepsFile = testProject.buildDir.resolve("DependencyConstraints")
        assertThat(cordappDepsFile).isRegularFile()
        val cordappDependencyConstraints = cordappDepsFile.toFile().inputStream()
            .use(::loadDependencyConstraints)
        assertThat(cordappDependencyConstraints)
            .noneMatch { it.fileName == "biz.aQute.bnd.annotation-${bndVersion}.jar" }
            .noneMatch { it.fileName == "slf4j-api-${librarySlf4jVersion}.jar" }
            .noneMatch { it.fileName == "slf4j-api-${cordaSlf4jVersion}.jar" }
            .anyMatch { it.fileName == "guava-${libraryGuavaVersion}.jar" }
            .anyMatch { it.fileName == "commons-io-$commonsIoVersion.jar" }
            .anyMatch { it.fileName == "library.jar" }
    }
}
