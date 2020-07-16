package net.corda.plugins

import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.ThreadLocalToggleField
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

class CordformTest : BaseformTest() {
    @Test
    fun `network parameter overrides`() {
        val financeReleaseVersion = "4.0"
        val cordaReleaseVersion = "4.3"

        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithNetworkParameterOverrides.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion", "-Pfinance_release_version=$financeReleaseVersion"
        )
        installResource("testkeystore")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, "corda-finance-workflows-$financeReleaseVersion")).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, "corda-finance-contracts-$financeReleaseVersion")).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()

        ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
        net.corda.core.serialization.internal._contextSerializationEnv.set(SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPParametersSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
        )
        val serializedBytes = SerializedBytes<SignedDataWithCert<NetworkParameters>>(getNetworkParameterOverrides(notaryNodeName).toFile().readBytes())
        val deserializedNetworkParameterOverrides = serializedBytes.deserialize(SerializationDefaults.SERIALIZATION_FACTORY).raw.deserialize()
        val deserializedPackageOwnership = deserializedNetworkParameterOverrides.packageOwnership
        assertThat(deserializedPackageOwnership.containsKey("com.mypackagename")).isTrue()
        Assertions.assertEquals(Duration.ofDays(2), deserializedNetworkParameterOverrides.eventHorizon)
        Assertions.assertEquals(123456, deserializedNetworkParameterOverrides.maxMessageSize)
        Assertions.assertEquals(2468, deserializedNetworkParameterOverrides.maxTransactionSize)
        Assertions.assertEquals(3, deserializedNetworkParameterOverrides.minimumPlatformVersion)
    }

    @Test
    fun `a node with cordapp dependency - backwards compatibility`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappBackwardsCompatibility.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `a node that requires an extra command to create schema`() {
        val cordaReleaseVersion = "4.6-20200608_123659-1da174c"

        val runner = getStandardGradleRunnerFor(
            "DeploySingleNodeWithExtraCommandForDbSchema.gradle",
            "deployNodes",
            "-Pcorda_release_version=$cordaReleaseVersion"
        )

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeLogFile(notaryNodeName, "node-run-migration.log")).isRegularFile()
        assertThat(getNodeLogFile(notaryNodeName, "node-schema-cordform.log")).isRegularFile()
        assertThat(getNodeLogFile(notaryNodeName, "node-info-gen.log")).isRegularFile()
    }

    @Test
    fun `a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordapp.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
    }

    @Test
    fun `a node with cordapp dependency with OU in name`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappWithOU.gradle")

        val result = runner.build()
        val notaryFullName = "${notaryNodeName}_${notaryNodeUnitName}"

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryFullName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryFullName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryFullName)).isRegularFile()
    }

    @Test
    fun `deploy a node with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
    }

    @Test
    fun `deploy the locally built cordapp with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithLocallyBuildCordappAndConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, localCordappJarName)).isRegularFile()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappJarName)).isRegularFile()
    }

    @Test
    fun `regex matching used by verifyAndGetRuntimeJar()`() {
        val jarName = "corda"

        var releaseVersion = "4.3"
        var pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3.jarBla").doesNotContainPattern(pattern)
        assertThat("bla\\bla\\bla\\corda-4.3.jar").containsPattern(pattern)
        assertThat("corda-4.3-jdk11.jar").containsPattern(pattern)
        assertThat("corda-4.3jdk11.jar").doesNotContainPattern(pattern)
        assertThat("bla\\bla\\bla\\corda-enterprise-4.3.jar").containsPattern(pattern)
        assertThat("corda-enterprise-4.3.jar").containsPattern(pattern)
        assertThat("corda-enterprise-4.3-jdk11.jar").containsPattern(pattern)

        releaseVersion = "4.3-RC01"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3-RC01.jar").containsPattern(pattern)
        assertThat("corda-4.3RC01.jar").doesNotContainPattern(pattern)

        releaseVersion = "4.3.20190925"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex().pattern
        assertThat("corda-4.3.20190925.jar").containsPattern(pattern)
        assertThat("corda-4.3.20190925-TEST.jar").containsPattern(pattern)
    }
}
