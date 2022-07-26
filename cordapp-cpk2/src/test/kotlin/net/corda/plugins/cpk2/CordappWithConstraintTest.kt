package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CordappWithConstraintTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val library1Version = "1.2.3-SNAPSHOT"
        private const val library2Version = "1.2.4-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-with-constraint")
            .withSubResource("src/main/kotlin/com/example/constraint/ConstraintContract.kt")
            .withSubResource("library1/src/main/java/org/testing/compress/package-info.java")
            .withSubResource("library1/src/main/java/org/testing/compress/ExampleZip.java")
            .withSubResource("library1/build.gradle")
            .withSubResource("library2/src/main/java/org/testing/io/package-info.java")
            .withSubResource("library2/src/main/java/org/testing/io/ExampleStream.java")
            .withSubResource("library2/build.gradle")
            .build(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Plibrary1_version=$library1Version",
                "-Plibrary2_version=$library2Version",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcommons_codec_version=$commonsCodecVersion",
                "-Pcommons_compress_version=$commonsCompressVersion"
            )
    }

    @Test
    fun testLibraries() {
        assertThat(testProject.libraries)
            .noneMatch { it.startsWith("commons-compress-") }
            .noneMatch { it.startsWith("library1-") }
            .anyMatch { it == "commons-io-$commonsIoVersion.jar" }
            .anyMatch { it == "commons-codec-$commonsCodecVersion.jar" }
            .anyMatch { it == "library2-$library2Version.jar" }
            .hasSizeGreaterThanOrEqualTo(3)
    }
}
