package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.junit.Ignore
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class DockerformTest : BaseformTest() {


//    @Test
//    fun `a node with cordapp dependency`() {
//        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordapp.gradle", "prepareDockerNodes")
//
//        val result = runner.build()
//
//        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
//        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
//        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
//        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
//    }

    @Test
    fun `create docker compose file for cordapp` () {
        val runner = getStandardGradleRunnerFor("DeployCordappWithDocker.gradle", "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }
}