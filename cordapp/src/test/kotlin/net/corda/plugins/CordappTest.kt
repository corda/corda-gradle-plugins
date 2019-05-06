package net.corda.plugins

import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarInputStream

class CordappTest {
    @TempDir
    lateinit var testProjectDir: Path
    private lateinit var buildFile: Path

    private companion object {
        const val cordappJarName = "test-cordapp"

        private val testGradleUserHome = systemProperty("test.gradle.user.home")
    }

    @BeforeEach
    fun setup() {
        buildFile = testProjectDir.resolve("build.gradle")
        installResource(testProjectDir, "settings.gradle")
        installResource(testProjectDir, "repositories.gradle")
    }

    @Test
    fun `a cordapp with a cordapp info block`() {
        val expectedName = "test cordapp"
        val expectedVersion = "3.2.1"
        val expectedVendor = "test vendor"
        val expectedtargetPlatformVersion = "5"
        val expectedminimumPlatformVersion = "2"

        val extraArgs = listOf(
                "-Pname_info_arg=$expectedName",
                "-Pversion_info_arg=$expectedVersion",
                "-Pvendor_info_arg=$expectedVendor",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion",
                "-Pmin_platform_version_arg=$expectedminimumPlatformVersion")

        val jarTaskRunner = jarTaskRunner("CorDappWithInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).isRegularFile()

        JarInputStream(jarFile.toFile().inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Name")).isEqualTo(expectedName)
            assertThat(attributes.getValue("Implementation-Version")).isEqualTo(expectedVersion)
            assertThat(attributes.getValue("Implementation-Vendor")).isEqualTo(expectedVendor)
            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
            assertThat(attributes.getValue("Min-Platform-Version")).isEqualTo(expectedminimumPlatformVersion)
        }
    }

    @Test
    fun `a cordapp with a cordapp contract info block`() {
        val expectedContractCordappName = "test contract name"
        val expectedContractCordappVersion = "1"
        val expectedContractCordappVendor = "test contract vendor"
        val expectedContractCordappLicence = "test contract licence"
        val expectedtargetPlatformVersion = "4"

        val extraArgs = listOf(
                "-PcordappContractName_info_arg=$expectedContractCordappName",
                "-PcordappContractVersion_info_arg=$expectedContractCordappVersion",
                "-PcordappContractVendor_info_arg=$expectedContractCordappVendor",
                "-PcordappContractLicence_info_arg=$expectedContractCordappLicence",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion"
        )

        val jarTaskRunner = jarTaskRunner("CorDappWithContractInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).isRegularFile()

        JarInputStream(jarFile.toFile().inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Cordapp-Contract-Name")).isEqualTo(expectedContractCordappName)
            assertThat(attributes.getValue("Cordapp-Contract-Version")).isEqualTo(expectedContractCordappVersion)
            assertThat(attributes.getValue("Cordapp-Contract-Vendor")).isEqualTo(expectedContractCordappVendor)
            assertThat(attributes.getValue("Cordapp-Contract-Licence")).isEqualTo(expectedContractCordappLicence)
            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
        }
    }

    @Test
    fun `a cordapp with a cordapp workflow info block`() {
        val expectedWorkflowCordappName = "test workflow name"
        val expectedWorkflowCordappVersion = "2"
        val expectedWorkflowCordappVendor = "test workflow vendor"
        val expectedWorkflowCordappLicence = "test workflow licence"
        val expectedtargetPlatformVersion = "4"

        val extraArgs = listOf(
                "-PcordappWorkflowName_info_arg=$expectedWorkflowCordappName",
                "-PcordappWorkflowVersion_info_arg=$expectedWorkflowCordappVersion",
                "-PcordappWorkflowVendor_info_arg=$expectedWorkflowCordappVendor",
                "-PcordappWorkflowLicence_info_arg=$expectedWorkflowCordappLicence",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion"
        )

        val jarTaskRunner = jarTaskRunner("CorDappWithWorkflowInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).isRegularFile()

        JarInputStream(jarFile.toFile().inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Cordapp-Workflow-Name")).isEqualTo(expectedWorkflowCordappName)
            assertThat(attributes.getValue("Cordapp-Workflow-Version")).isEqualTo(expectedWorkflowCordappVersion)
            assertThat(attributes.getValue("Cordapp-Workflow-Vendor")).isEqualTo(expectedWorkflowCordappVendor)
            assertThat(attributes.getValue("Cordapp-Workflow-Licence")).isEqualTo(expectedWorkflowCordappLicence)
            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
        }
    }

    @Test
    fun `a cordapp with a cordapp contract and workflow info block`() {
        val expectedContractCordappName = "test contract name"
        val expectedContractCordappVersion = "1"
        val expectedContractCordappVendor = "test contract vendor"
        val expectedContractCordappLicence = "test contract licence"
        val expectedWorkflowCordappName = "test workflow name"
        val expectedWorkflowCordappVersion = "2"
        val expectedWorkflowCordappVendor = "test workflow vendor"
        val expectedWorkflowCordappLicence = "test workflow licence"
        val expectedtargetPlatformVersion = "4"

        val extraArgs = listOf(
                "-PcordappContractName_info_arg=$expectedContractCordappName",
                "-PcordappContractVersion_info_arg=$expectedContractCordappVersion",
                "-PcordappContractVendor_info_arg=$expectedContractCordappVendor",
                "-PcordappContractLicence_info_arg=$expectedContractCordappLicence",
                "-PcordappWorkflowName_info_arg=$expectedWorkflowCordappName",
                "-PcordappWorkflowVersion_info_arg=$expectedWorkflowCordappVersion",
                "-PcordappWorkflowVendor_info_arg=$expectedWorkflowCordappVendor",
                "-PcordappWorkflowLicence_info_arg=$expectedWorkflowCordappLicence",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion"
        )

        val jarTaskRunner = jarTaskRunner("CorDappWithContractAndWorflowInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).isRegularFile()

        JarInputStream(jarFile.toFile().inputStream()).use { jar ->
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
        }
    }

    @Test
    fun `a cordapp with a all cordapp info blocks`() {
        val expectedName = "test cordapp"
        val expectedVersion = "3.2.1"
        val expectedVendor = "test vendor"
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
                "-Pname_info_arg=$expectedName", "-Pversion_info_arg=$expectedVersion", "-Pvendor_info_arg=$expectedVendor",
                "-PcordappContractName_info_arg=$expectedContractCordappName", "-PcordappContractVersion_info_arg=$expectedContractCordappVersion", "-PcordappContractVendor_info_arg=$expectedContractCordappVendor", "-PcordappContractLicence_info_arg=$expectedContractCordappLicence",
                "-PcordappWorkflowName_info_arg=$expectedWorkflowCordappName", "-PcordappWorkflowVersion_info_arg=$expectedWorkflowCordappVersion", "-PcordappWorkflowVendor_info_arg=$expectedWorkflowCordappVendor", "-PcordappWorkflowLicence_info_arg=$expectedWorkflowCordappLicence",
                "-Ptarget_version_arg=$expectedtargetPlatformVersion", "-Pmin_platform_version_arg=$expectedminimumPlatformVersion")

        val jarTaskRunner = jarTaskRunner("CorDappWithInfoAll.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).isRegularFile()

        JarInputStream(jarFile.toFile().inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Cordapp-Contract-Name")).isEqualTo(expectedContractCordappName)
            assertThat(attributes.getValue("Cordapp-Contract-Version")).isEqualTo(expectedContractCordappVersion)
            assertThat(attributes.getValue("Cordapp-Contract-Vendor")).isEqualTo(expectedContractCordappVendor)
            assertThat(attributes.getValue("Cordapp-Contract-Licence")).isEqualTo(expectedContractCordappLicence)

            assertThat(attributes.getValue("Cordapp-Workflow-Name")).isEqualTo(expectedWorkflowCordappName)
            assertThat(attributes.getValue("Cordapp-Workflow-Version")).isEqualTo(expectedWorkflowCordappVersion)
            assertThat(attributes.getValue("Cordapp-Workflow-Vendor")).isEqualTo(expectedWorkflowCordappVendor)
            assertThat(attributes.getValue("Cordapp-Workflow-Licence")).isEqualTo(expectedWorkflowCordappLicence)

            assertThat(attributes.getValue("Name")).isNull()
            assertThat(attributes.getValue("Implementation-Version")).isNull()
            assertThat(attributes.getValue("Implementation-Vendor")).isNull()

            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
            assertThat(attributes.getValue("Min-Platform-Version")).isEqualTo(expectedminimumPlatformVersion)
            assertThat(attributes.getValue("Sealed")).isEqualTo(expectedSealed)
        }
    }

    @Test
    fun `a cordapp without any metadata`() {
        val jarTaskRunner = jarTaskRunner("CorDappWithoutMetadata.gradle")

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).exists()
    }

    private fun jarTaskRunner(buildFileResourceName: String, extraArgs: List<String> = emptyList()): GradleRunner {
        createBuildFile(buildFileResourceName)
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(listOf("jar", "-s", "--info", "-g", testGradleUserHome) + extraArgs)
                .withPluginClasspath()
    }

    private fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile.toFile().outputStream())
    private fun getCordappJar(cordappJarName: String): Path = Paths.get(testProjectDir.toFile().absolutePath, "build", "libs", "$cordappJarName.jar")
}