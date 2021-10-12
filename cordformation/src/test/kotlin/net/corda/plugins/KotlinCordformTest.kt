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
            .isRegularFile
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile

        // Check Bank node deployment
        assertThat(getNodeCordappJar(bankNodeName, cordaFinanceContractsJarName))
            .isRegularFile
        assertThat(getNodeCordappJar(bankNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile
        assertThat(getNodeCordappConfig(bankNodeName, cordaFinanceWorkflowsJarName))
            .isRegularFile
    }
}
