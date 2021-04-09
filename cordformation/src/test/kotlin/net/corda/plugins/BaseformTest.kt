package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

open class BaseformTest {
    @TempDir
    lateinit var testProjectDir: Path

    private lateinit var testRoot: String

    companion object {
        private const val cordaVersion = "1618234704308-rc"
        const val cordaBundleVersion = "1.0.$cordaVersion"
        const val cordaReleaseVersion = "5.0.$cordaVersion"
        const val cordaFinanceWorkflowsCpkName = "corda-finance-workflows-$cordaBundleVersion-cordapp"
        const val cordaFinanceContractsCpkName = "corda-finance-contracts-$cordaBundleVersion-cordapp"
        const val localCordappCpkName = "locally-built-cordapp"
        const val bankNodeName = "BankOfCorda"
        const val notaryNodeName = "NotaryService"
        const val notaryNodeUnitName = "OrgUnit"

        private val testGradleUserHome = System.getProperty("test.gradle.user.home", ".")
    }

    fun getStandardGradleRunnerFor(
            buildFileResourceName: String,
            taskName: String,
            vararg extraArgs: String
    ): GradleRunner {
        val isKotlin = buildFileResourceName.endsWith(".kts")

        val buildScriptName = if (isKotlin) {
            "build.gradle.kts"
        } else {
            "build.gradle"
        }
        val buildFile = testProjectDir.resolve(buildScriptName)

        createBuildFile(buildFileResourceName, buildFile)
        testRoot = buildFileResourceName.replaceAfterLast('/', "")
        if (!installResource("$testRoot/settings.gradle")) {
            installResource("settings.gradle")
        }
        installResource("repositories.gradle")
        installResource("gradle.properties")
        installResource("postgres.gradle")
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName, "-s", "--info", "-g", testGradleUserHome, *extraArgs)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String, buildFile: Path) {
        if (copyResourceTo(buildFile, buildFileResourceName) < 0) {
            throw NoSuchFileException(buildFileResourceName)
        }
    }

    fun installResource(resourceName: String): Boolean {
        val filePath = resourceName.removePrefix(testRoot).dropWhile { it == '/' }
        val fileIdx = filePath.lastIndexOf('/')
        val directory = if (fileIdx == -1) {
            testProjectDir
        } else {
            Files.createDirectories(testProjectDir.resolve(filePath.substring(0, fileIdx)))
        }
        val buildFile = directory.resolve(filePath.substring(fileIdx + 1))
        return copyResourceTo(buildFile, resourceName) >= 0
    }

    private fun copyResourceTo(target: Path, resourceName: String): Long {
        return javaClass.classLoader.getResourceAsStream(resourceName)?.use { input ->
            Files.copy(input, target)
        } ?: -1
    }

    fun getNodeLogFile( nodeName: String, fileName: String ): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "logs", fileName)
    fun getNodeCordappDir(nodeName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "cordapps")
    fun getNodeCordappCpk(nodeName: String, cordappCpkName: String): Path = getNodeCordappDir(nodeName).resolve("$cordappCpkName.cpk")
    fun getNodeCordappConfig(nodeName: String, cordappCpkName: String): Path = getNodeCordappDir(nodeName).resolve("config").resolve("$cordappCpkName.conf")
    fun getNetworkParameterOverrides(nodeName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "network-parameters")
    fun getNodeConfigFile(nodeName: String): Path = Paths.get(testProjectDir.toAbsolutePath().toString(), "build", "nodes", nodeName, "node.conf")

    fun getNodeConfig(nodeName: String): Config {
        val configFile = getNodeConfigFile(nodeName)
        assertThat(configFile).isRegularFile()
        return ConfigFactory.parseFile(configFile.toFile())
    }

//    class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme() {
//        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
//        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
//
//        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
//            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
//        }
//    }
}
