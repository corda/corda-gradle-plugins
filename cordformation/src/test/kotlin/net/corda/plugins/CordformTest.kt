package net.corda.plugins

import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.ThreadLocalToggleField
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CordformTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()
    private lateinit var buildFile: File

    private companion object {
        const val cordaFinanceWorkflowsJarName = "corda-finance-workflows-4.0"
        const val cordaFinanceContractsJarName = "corda-finance-contracts-4.0"
        const val localCordappJarName = "locally-built-cordapp"
        const val notaryNodeName = "Notary Service"

        private val testGradleUserHome = System.getProperty("test.gradle.user.home", ".")
    }

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
    }

    @Test
    fun `network parameter overrides`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithNetworkParameterOverrides.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).exists()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).exists()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).exists()

        ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
        net.corda.core.serialization.internal._contextSerializationEnv.set(SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPParametersSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
        )
        val serializedBytes = SerializedBytes<SignedDataWithCert<NetworkParameters>>(getNetworkParameterOverrides(notaryNodeName).readBytes())
        val deserialized = serializedBytes.deserialize(SerializationDefaults.SERIALIZATION_FACTORY).raw.deserialize().packageOwnership
        assertThat(deserialized.containsKey("com.mypackagename")).isTrue()
    }

    @Test
    fun `a node with cordapp dependency - backwards compatibility`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappBackwardsCompatibility.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).exists()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).exists()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).exists()
    }

    @Test
    fun `a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordapp.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).exists()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).exists()
        assertThat(getNetworkParameterOverrides(notaryNodeName)).exists()
    }

    @Test
    fun `deploy a node with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceWorkflowsJarName)).exists()
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceContractsJarName)).exists()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceWorkflowsJarName)).exists()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceContractsJarName)).exists()
    }

    @Test
    fun `deploy the locally built cordapp with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithLocallyBuildCordappAndConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, localCordappJarName)).exists()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappJarName)).exists()
    }

    private fun getStandardGradleRunnerFor(buildFileResourceName: String): GradleRunner {
        createBuildFile(buildFileResourceName)
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.root)
                .withArguments("deployNodes", "-s", "--info", "-g", testGradleUserHome)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile.outputStream())
    private fun getNodeCordappJar(nodeName: String, cordappJarName: String) = File(testProjectDir.root, "build/nodes/$nodeName/cordapps/$cordappJarName.jar")
    private fun getNodeCordappConfig(nodeName: String, cordappJarName: String) = File(testProjectDir.root, "build/nodes/$nodeName/cordapps/config/$cordappJarName.conf")
    private fun getNetworkParameterOverrides(nodeName: String) = File(testProjectDir.root, "build/nodes/$nodeName/network-parameters")

    private class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
        }
    }
}