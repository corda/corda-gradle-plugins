package net.corda.plugins.cpb2

import java.nio.file.Path
import net.corda.plugins.cpk2.CPB_TASK_NAME
import net.corda.plugins.cpk2.CPK_TASK_NAME
import net.corda.plugins.cpk2.GradleProject
import net.corda.plugins.cpk2.VERIFY_BUNDLE_TASK_NAME
import net.corda.plugins.cpk2.cordaApiVersion
import net.corda.plugins.cpk2.expectedCordappContractVersion
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir

@TestInstance(PER_CLASS)
class CpbDisableCpkTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cpb-disable-cpk")
            .withSubResource("src/main/java/com/example/cpb/nocpk/ExampleContract.java")
            .build(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion"
            )
    }

    @Test
    fun testSourceIsCompiled() {
        assertThat(testProject.outcomeOf(COMPILE_JAVA_TASK_NAME)).isEqualTo(SUCCESS)
    }

    @Test
    fun testJarIsBuilt() {
        assertThat(testProject.outcomeOf(JAR_TASK_NAME)).isEqualTo(SUCCESS)
    }

    @Test
    fun testJarIsVerified() {
        assertThat(testProject.outcomeOf(VERIFY_BUNDLE_TASK_NAME)).isEqualTo(SUCCESS)
    }

    @Test
    fun testCpkIsSkipped() {
        assertThat(testProject.outcomeOf(CPK_TASK_NAME)).isEqualTo(SKIPPED)
    }

    @Test
    fun testCpbIsSkipped() {
        assertThat(testProject.outcomeOf(CPB_TASK_NAME)).isEqualTo(SKIPPED)
    }
}
