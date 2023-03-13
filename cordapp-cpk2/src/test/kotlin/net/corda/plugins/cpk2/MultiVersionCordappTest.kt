package net.corda.plugins.cpk2

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir

@TestInstance(PER_CLASS)
class MultiVersionCordappTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("multi-version-cordapp")
            .withSubResource("src/main/java/com/example/multi/ExampleContract.java")
            .withSubResource("src/main/java11/com/example/multi/ExampleContract.java")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion"
            )
    }

    @Test
    fun testFixupWarningWhenCreatingJar() {
        assertThat(testProject.outputLines).anyMatch { line ->
            line.endsWith(": Classes found in the wrong directory: {META-INF/versions/11/com/example/multi/ExampleContract.class=com.example.multi.ExampleContract}")
        }
    }
}
