package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class DockerformTest : BaseformTest() {

    @Test
    fun `deploy a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithCordappWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp dependency and network configuration` () {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithCordappWithNetworkConfigWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp` () {
        val runner = getStandardGradleRunnerFor(
                "DeployThreeNodeCordappWithExternalDBSettingsWithDocker.gradle",
                "prepareDockerNodes")

        val bankOfCordaNodeName = "BankOfCorda"
        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankOfCordaNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a three node cordapp with network configuration` () {
        val runner = getStandardGradleRunnerFor(
                "DeployThreeNodeCordappWithExternalDBSettingsWithNetworkConfigWithDocker.gradle",
                "prepareDockerNodes")

        val bankOfCordaNodeName = "BankOfCorda"
        val bigCorporationNodeName = "BigCorporation"

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bankOfCordaNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bankOfCordaNodeName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(bigCorporationNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(bigCorporationNodeName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp dependency with db settings`() {
        val runner = getStandardGradleRunnerFor(
                "DeploySingleNodeWithExternalDBSettingsWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `deploy two nodes with cordapp dependencies`() {
        val runner = getStandardGradleRunnerFor(
                "DeployTwoNodeCordappWithDocker.gradle",
                "prepareDockerNodes")

        val result = runner.build()

        assertThat(result.task(":prepareDockerNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }
}