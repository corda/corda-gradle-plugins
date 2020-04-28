package net.corda.plugins

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class DockerImageTest :BaseformTest() {
    @Test
    fun testThatImageBuildsDockerFileAsExpected() {
        val runner = getStandardGradleRunnerFor("DeployDockerImage.gradle", "dockerImage")

        val result = runner.build()
        Assertions.assertThat(result.task(":dockerImage")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        Assertions.assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker", "dockerfile")).isRegularFile()

        val dockerfile = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker", "dockerfile").toFile()
        val text = dockerfile.readText()
        Assertions.assertThat(text.contains("From corda/entImage")).isEqualTo(true)
        Assertions.assertThat(text.contains("COPY *.jar /opt/corda/cordapps/")).isEqualTo(true)
    }

    @Test
    fun testThatCordappDependenciesArePulledDownIntoTheBuildDockerDir() {
        val runner = getStandardGradleRunnerFor("DeployDockerImage.gradle", "dockerImage")

        val result = runner.build()
        Assertions.assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker","corda-finance-contracts-4.0.jar")).isRegularFile()
        Assertions.assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker","corda-finance-workflows-4.0.jar")).isRegularFile()
    }
}
