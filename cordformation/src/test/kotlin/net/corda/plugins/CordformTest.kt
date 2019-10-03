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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class CordformTest : BaseformTest() {


    @Disabled
    @Test
    fun `network parameter overrides`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithNetworkParameterOverrides.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()

        ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
        net.corda.core.serialization.internal._contextSerializationEnv.set(SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPParametersSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
        )
        val serializedBytes = SerializedBytes<SignedDataWithCert<NetworkParameters>>(getNetworkParameterOverrides(notaryNodeName).toFile().readBytes())
        val deserialized = serializedBytes.deserialize(SerializationDefaults.SERIALIZATION_FACTORY).raw.deserialize().packageOwnership
        assertThat(deserialized.containsKey("com.mypackagename")).isTrue()
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
    fun `a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordapp.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).isRegularFile()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).isRegularFile()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).isRegularFile()
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
        var pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex()
        assertThat("corda-4.3.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3.jarBla".contains(pattern)).isFalse()
        assertThat("bla\\bla\\bla\\corda-4.3.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3-jdk11.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3jdk11.jar".contains(pattern)).isFalse()
        assertThat("bla\\bla\\bla\\corda-enterprise-4.3.jar".contains(pattern)).isTrue()
        assertThat("corda-enterprise-4.3.jar".contains(pattern)).isTrue()
        assertThat("corda-enterprise-4.3-jdk11.jar".contains(pattern)).isTrue()

        releaseVersion = "4.3-RC01"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex()
        assertThat("corda-4.3-RC01.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3RC01.jar".contains(pattern)).isFalse()

        releaseVersion = "4.3.20190925"
        pattern = "\\Q$jarName\\E(-enterprise)?-\\Q$releaseVersion\\E(-.+)?\\.jar\$".toRegex()
        assertThat("corda-4.3.20190925.jar".contains(pattern)).isTrue()
        assertThat("corda-4.3.20190925-TEST.jar".contains(pattern)).isTrue()
    }
}