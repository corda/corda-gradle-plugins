package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class DockerImageTest :BaseformTest() {
    @Test
    fun testThatImageBuildsDockerFileAsExpected() {
        val runner = getStandardGradleRunnerFor("DeployDockerImage.gradle", "dockerImage")

        val result = runner.build()
        assertThat(result.task(":dockerImage")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker", "Dockerfile")).isRegularFile

        val dockerfile = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker", "Dockerfile").toFile()
        val text = dockerfile.readText()
        assertThat(text.contains("FROM corda/entImage")).isEqualTo(true)
        assertThat(text.contains("COPY *.jar /opt/corda/cordapps/")).isEqualTo(true)
    }

    @Test
    fun testThatCordappDependenciesArePulledDownIntoTheBuildDockerDir() {
        val runner = getStandardGradleRunnerFor("DeployDockerImage.gradle", "dockerImage")
        installResource("dummyJar.jar")
        val result = runner.build()

        assertThat(result.task(":dockerImage")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker","corda-finance-contracts-4.8.jar")).isRegularFile
        assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker","corda-finance-workflows-4.8.jar")).isRegularFile
        assertThat(Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "docker","dummyJar.jar")).isRegularFile
    }
}
