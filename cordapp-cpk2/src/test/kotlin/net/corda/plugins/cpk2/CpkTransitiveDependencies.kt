package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.CordappUtils.CPK_DEPENDENCIES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.osgi.framework.Constants.BUNDLE_LICENSE
import org.osgi.framework.Constants.BUNDLE_NAME
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VENDOR
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.framework.Constants.EXPORT_PACKAGE
import org.osgi.framework.Constants.IMPORT_PACKAGE
import org.osgi.framework.Constants.REQUIRE_CAPABILITY
import java.nio.file.Path

/**
 * Verify that transitive cordapp and cordaProvided dependencies
 * are inherited by downstream CPK projects.
 */
@TestInstance(PER_CLASS)
class CpkTransitiveDependencies {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpk2Version = "2.0-SNAPSHOT"
        private const val cpk1Type = "foo"
        private const val cpk2Type = "api"
        private const val cordaNotaryPluginVersion = "5.2.0.0"
    }

    private fun buildProject(
        taskName: String,
        testProjectDir: Path,
        reporter: TestReporter
    ): GradleProject {
        val repositoryDir = testProjectDir.resolve("maven")
        return GradleProject(testProjectDir, reporter)
            .withTestName("cpk-transitive-dependencies")
            .withSubResource("cpk-one/build.gradle")
            .withSubResource("cpk-two/build.gradle")
            .withTaskName(taskName)
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcommons_io_version=$commonsIoVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcordapp_version=$cordappVersion",
                "-Pcorda_notary_plugin_version=$cordaNotaryPluginVersion",
                "-Pcpk1_version=$cpk1Version",
                "-Pcpk2_version=$cpk2Version",
                "-Prepository_dir=$repositoryDir",
                "-Pcpk1_type=$cpk1Type",
                "-Pcpk2_type=$cpk2Type"
            )
    }

    @Test
    fun transitivesTest(
        @TempDir testProjectDir: Path,
        reporter: TestReporter
    ) {
        val testProject = buildProject("assemble", testProjectDir, reporter)
        assertThat(testProject.cpkDependencies)
            .anyMatch { it.name == "com.example.cpk-one" && it.version == toOSGi(cpk1Version) && it.type == cpk1Type }
            .anyMatch { it.name == "com.example.cpk-two" && it.version == toOSGi(cpk2Version) && it.type == cpk2Type }
            .anyMatch { it.name == "com.r3.corda.notary.plugin.nonvalidating.notary-plugin-non-validating-client" && it.version == cordaNotaryPluginVersion }
            .anyMatch { it.name == "com.r3.corda.notary.plugin.nonvalidating.notary-plugin-non-validating-api" && it.version == cordaNotaryPluginVersion }
            .anyMatch { it.name == "com.r3.corda.notary.plugin.common.notary-plugin-common" && it.version == cordaNotaryPluginVersion }
            .hasSize(5)
    }
}
