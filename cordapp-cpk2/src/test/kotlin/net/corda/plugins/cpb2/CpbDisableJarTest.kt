package net.corda.plugins.cpb2

import java.nio.file.Path
import net.corda.plugins.cpk.CPB_TASK_NAME
import net.corda.plugins.cpk.CPK_TASK_NAME
import net.corda.plugins.cpk.GradleProject
import net.corda.plugins.cpk.VERIFY_BUNDLE_TASK_NAME
import net.corda.plugins.cpk.cordaApiVersion
import net.corda.plugins.cpk.expectedCordappContractVersion
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir

@TestInstance(PER_CLASS)
class CpbDisableJarTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cpb-disable-jar")
            .withTaskOutcome(UP_TO_DATE)
            .withSubResource("src/main/java/com/example/cpb/nojar/ExampleContract.java")
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
    fun testJarIsSkipped() {
        assertThat(testProject.outcomeOf(JAR_TASK_NAME)).isEqualTo(SKIPPED)
    }

    @Test
    fun testVerifyIsSkipped() {
        assertThat(testProject.outcomeOf(VERIFY_BUNDLE_TASK_NAME)).isEqualTo(SKIPPED)
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
