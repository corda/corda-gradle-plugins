package net.corda.plugins

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import org.apache.commons.io.IOUtils
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

open class BaseformTest {
    @TempDir
    lateinit var testProjectDir: Path
    lateinit var buildFile: Path

    companion object {
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


    fun getStandardGradleRunnerFor(buildFileResourceName: String, taskName: String = "deployNodes"): GradleRunner {
        createBuildFile(buildFileResourceName)
        installResource("settings.gradle")
        installResource("gradle.properties")
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName, "-s", "--info", "-g", testGradleUserHome)
                .withPluginClasspath()
    }

    fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile.toFile().outputStream())
    fun installResource(resourceName: String) {
        val buildFile = testProjectDir.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
        javaClass.classLoader.getResourceAsStream(resourceName)?.use { input ->
            IOUtils.copy(input, buildFile.toFile().outputStream())
        } ?: throw FileNotFoundException(resourceName)
    }

    fun getNodeCordappJar(nodeName: String, cordappJarName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "$cordappJarName.jar")
    fun getNodeCordappConfig(nodeName: String, cordappJarName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "config", "$cordappJarName.conf")
    fun getNetworkParameterOverrides(nodeName: String) = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "network-parameters")

    class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
        }
    }

}