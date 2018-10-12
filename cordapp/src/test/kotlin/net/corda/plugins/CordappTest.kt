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
        val expectedName = "test cordapp"
        val expectedVersion = "3.2.1"
        val expectedVendor = "test vendor"
        val expectedtargetPlatformVersion = "5"
        val expectedminimumPlatformVersion = "2"
        val expectedSealed = "true"

        val extraArgs = listOf("-Pname_info_arg=$expectedName", "-Pversion_info_arg=$expectedVersion", "-Pvendor_info_arg=$expectedVendor", "-Ptarget_version_arg=$expectedtargetPlatformVersion", "-Pmin_platform_version_arg=$expectedminimumPlatformVersion")

        val jarTaskRunner = jarTaskRunner("CorDappWithInfo.gradle", extraArgs)

        val result = jarTaskRunner.build()

        assertThat(result.task(":jar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val jarFile = getCordappJar(cordappJarName)
        assertThat(jarFile).exists()

        JarInputStream(jarFile.inputStream()).use { jar ->
            val attributes = jar.manifest.mainAttributes

            assertThat(attributes.getValue("Name")).isEqualTo(expectedName)
            assertThat(attributes.getValue("Implementation-Version")).isEqualTo(expectedVersion)
            assertThat(attributes.getValue("Target-Platform-Version")).isEqualTo(expectedtargetPlatformVersion)
            assertThat(attributes.getValue("Min-Platform-Version")).isEqualTo(expectedminimumPlatformVersion)
            assertThat(attributes.getValue("Implementation-Vendor")).isEqualTo(expectedVendor)
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