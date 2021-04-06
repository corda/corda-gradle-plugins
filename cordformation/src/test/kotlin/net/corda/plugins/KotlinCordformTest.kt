package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Test

class KotlinCordformTest : BaseformTest() {
    @Test
    fun `two nodes with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor("DeployTwoNodeCordapp.gradle.kts")

        val result = runner.build()

        // Check task succeeded
        assertThat(result.task(":deployNodes")!!.outcome)
            .isEqualTo(SUCCESS)

        // Check Notary node deployment
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName))
            .isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, localCordappJarName))
            .doesNotExist()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappJarName))
            .doesNotExist()
        assertThatConfig(getNodeConfig(notaryNodeName))
            .hasPath("rpcSettings.address", "localhost:60001")
            .hasPath("rpcSettings.adminAddress", "localhost:60002")

        // Check Bank node deployment
        assertThat(getNodeCordappJar(bankNodeName, cordaFinanceContractsJarName))
            .isRegularFile()
        assertThat(getNodeCordappJar(bankNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(bankNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile()
        assertThat(getNodeCordappJar(bankNodeName, localCordappJarName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(bankNodeName, localCordappJarName))
            .isRegularFile()
        assertThatConfig(getNodeConfig(bankNodeName))
            .hasPath("rpcSettings.address", "localhost:10001")
            .hasPath("rpcSettings.adminAddress", "localhost:10002")
    }
}
