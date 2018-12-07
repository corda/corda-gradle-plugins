package net.corda.plugins

import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.util.jar.JarInputStream

class CordappTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()
    private lateinit var buildFile: File

    private companion object {
        const val cordappJarName = "test-cordapp"

        private val testGradleUserHome = System.getProperty("test.gradle.user.home", ".")
    }

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
    }

    @Test
    fun `a cordapp with a cordapp info block`() {
        val expectedtargetPlatformVersion = "5"
        val expectedminimumPlatformVersion = "2"
        val expectedSealed = "true"

        val expectedContractCordappName = "test contract name"
        val expectedContractCordappVersion = "1"
        val expectedContractCordappVendor = "test contract vendor"
        val expectedContractCordappLicence = "test contract licence"

        val expectedWorkflowCordappName = "test workflow name"
        val expectedWorkflowCordappVersion = "1"
        val expectedWorkflowCordappVendor = "test workflow vendor"
        val expectedWorkflowCordappLicence = "test workflow licence"

        val extraArgs = listOf(
                "-PcordappContractName_info_arg=$expectedContractCordappName", "-PcordappContractVersion_info_arg=$expectedContractCordappVersion", "-PcordappContractVendor_info_arg=$expectedContractCordappVendor", "-PcordappContractLicence_info_arg=$expectedContractCordappLicence",
                "-PcordappWorflowName_info_arg=$expectedWorkflowCordappName", "-PcordappWorflowVersion_info_arg=$expectedWorkflowCordappVersion", "-PcordappWorflowVendor_info_arg=$expectedWorkflowCordappVendor", "-PcordappWorflowLicence_info_arg=$expectedWorkflowCordappLicence",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion", "-Pmin_platform_version_arg=$expectedminimumPlatformVersion")

        val jarTaskRunner = jarTaskRunner("CorDappWithInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).exists()

        JarInputStream(jarFile.inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Cordapp-Contract-Name")).isEqualTo(expectedContractCordappName)
            assertThat(attributes.getValue("Cordapp-Contract-Version")).isEqualTo(expectedContractCordappVersion)
            assertThat(attributes.getValue("Cordapp-Contract-Vendor")).isEqualTo(expectedContractCordappVendor)
            assertThat(attributes.getValue("Cordapp-Contract-Licence")).isEqualTo(expectedContractCordappLicence)

            assertThat(attributes.getValue("Cordapp-Workflow-Name")).isEqualTo(expectedWorkflowCordappName)
            assertThat(attributes.getValue("Cordapp-Workflow-Version")).isEqualTo(expectedWorkflowCordappVersion)
            assertThat(attributes.getValue("Cordapp-Workflow-Vendor")).isEqualTo(expectedWorkflowCordappVendor)
            assertThat(attributes.getValue("Cordapp-Workflow-Licence")).isEqualTo(expectedWorkflowCordappLicence)

            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
            assertThat(attributes.getValue("Min-Platform-Version")).isEqualTo(expectedminimumPlatformVersion)
            assertThat(attributes.getValue("Sealed")).isEqualTo(expectedSealed)
        }
    }

    private fun jarTaskRunner(buildFileResourceName: String, extraArgs: List<String> = emptyList()): GradleRunner {
        createBuildFile(buildFileResourceName)
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(listOf("jar", "-s", "--info", "-g", testGradleUserHome) + extraArgs)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile.outputStream())
    private fun getCordappJar(cordappJarName: String) = File(testProjectDir.root, "build/libs/$cordappJarName.jar")
}