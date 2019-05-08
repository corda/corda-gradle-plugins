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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

class CordformTest {
    @TempDir
    lateinit var testProjectDir: Path
    private lateinit var buildFile: Path

    private companion object {
        const val cordaFinanceWorkflowsJarName = "corda-finance-workflows-4.0"
        const val cordaFinanceContractsJarName = "corda-finance-contracts-4.0"
        const val localCordappJarName = "locally-built-cordapp"
        const val notaryNodeName = "Notary Service"

        private val testGradleUserHome = System.getProperty("test.gradle.user.home", ".")
    }

    @BeforeEach
    fun setup() {
        buildFile = testProjectDir.resolve("build.gradle")
    }

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

    private fun getStandardGradleRunnerFor(buildFileResourceName: String): GradleRunner {
        createBuildFile(buildFileResourceName)
        installResource("settings.gradle")
        installResource("gradle.properties")
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments("deployNodes", "-s", "--info", "-g", testGradleUserHome)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile.toFile().outputStream())
    private fun installResource(resourceName: String) {
        val buildFile = testProjectDir.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
        javaClass.classLoader.getResourceAsStream(resourceName)?.use { input ->
            IOUtils.copy(input, buildFile.toFile().outputStream())
        } ?: throw FileNotFoundException(resourceName)
    }
    private fun getNodeCordappJar(nodeName: String, cordappJarName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "$cordappJarName.jar")
    private fun getNodeCordappConfig(nodeName: String, cordappJarName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "config", "$cordappJarName.conf")
    private fun getNetworkParameterOverrides(nodeName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "network-parameters")

    private class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
        }
    }
}