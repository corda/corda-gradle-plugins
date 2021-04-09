package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Test

class KotlinCordformTest : BaseformTest() {
    @Test
    fun `two nodes with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor(
            "DeployTwoNodeCordapp.gradle.kts",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion",
            "-Pcorda_bundle_version=$cordaBundleVersion"
        )

        val result = runner.build()

        // Check task succeeded
        assertThat(result.task(":deployNodes")!!.outcome)
            .isEqualTo(SUCCESS)

        // Check Notary node deployment
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceContractsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, cordaFinanceWorkflowsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappCpk(notaryNodeName, localCordappCpkName))
            .doesNotExist()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappCpkName))
            .doesNotExist()
        assertThatConfig(getNodeConfig(notaryNodeName))
            .hasPath("rpcSettings.address", "localhost:60001")
            .hasPath("rpcSettings.adminAddress", "localhost:60002")

        // Check Bank node deployment
        assertThat(getNodeCordappCpk(bankNodeName, cordaFinanceContractsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, cordaFinanceWorkflowsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(bankNodeName, cordaFinanceWorkflowsCpkName))
            .isRegularFile()
        assertThat(getNodeCordappCpk(bankNodeName, localCordappCpkName))
            .isRegularFile()
        assertThat(getNodeCordappConfig(bankNodeName, localCordappCpkName))
            .isRegularFile()
        assertThatConfig(getNodeConfig(bankNodeName))
            .hasPath("rpcSettings.address", "localhost:10001")
            .hasPath("rpcSettings.adminAddress", "localhost:10002")
    }
}
