package net.corda.plugins

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

open class BaseformTest {
    @TempDir
    lateinit var testProjectDir: Path
    lateinit var buildFile: Path

    companion object {
        const val cordaFinanceWorkflowsJarName = "corda-finance-workflows-4.3"
        const val cordaFinanceContractsJarName = "corda-finance-contracts-4.3"
        const val localCordappJarName = "locally-built-cordapp"
        const val notaryNodeName = "NotaryService"
        const val notaryNodeUnitName = "OrgUnit"

        private val testGradleUserHome = System.getProperty("test.gradle.user.home", ".")
    }

    @BeforeEach
    fun setup() {
        buildFile = testProjectDir.resolve("build.gradle")
    }


    fun getStandardGradleRunnerFor(
            buildFileResourceName: String,
            taskName: String = "deployNodes",
            vararg extraArgs: String
    ): GradleRunner {
        createBuildFile(buildFileResourceName)
        installResource("settings.gradle")
        installResource("repositories.gradle")
        installResource("gradle.properties")
        installResource("postgres.gradle")
        installResource("external-service.gradle")
        installResource("external-service-2.gradle")
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName, "-s", "--info", "-g", testGradleUserHome, *extraArgs)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String): Long = javaClass.getResourceAsStream(buildFileResourceName)?.use { s ->
        Files.copy(s, buildFile)
    } ?: throw NoSuchFileException(buildFileResourceName)

    fun installResource(resourceName: String) {
        val buildFile = testProjectDir.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
        javaClass.classLoader.getResourceAsStream(resourceName)?.use { input ->
            Files.copy(input, buildFile)
        } ?: throw NoSuchFileException(resourceName)
    }

    fun getNodeLogFile( nodeName: String, fileName: String ): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "logs", fileName)
    fun getNodeCordappJar(nodeName: String, cordappJarName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "$cordappJarName.jar")
    fun getNodeCordappConfig(nodeName: String, cordappJarName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps", "config", "$cordappJarName.conf")
    fun getNetworkParameterOverrides(nodeName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "network-parameters")
    fun getNodeConfig(nodeName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "node.conf")

    class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
        }
    }
}