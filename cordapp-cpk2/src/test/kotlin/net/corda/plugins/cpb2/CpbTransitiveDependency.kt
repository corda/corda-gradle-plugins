package net.corda.plugins.cpb2

import net.corda.plugins.cpk2.GradleProject
import net.corda.plugins.cpk2.annotationsVersion
import net.corda.plugins.cpk2.cordaApiVersion
import net.corda.plugins.cpk2.expectedCordappContractVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

@TestInstance(PER_CLASS)
class CpbTransitiveDependency {
    private companion object {
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpkFinalVersion = "3.0-SNAPSHOT"
        private const val cordaNotaryPluginVersion = "5.2.0.0"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cpb-transitive-dependencies")
            .withSubResource("cpk-one/build.gradle")
            .withSubResource("cpk-one/src/main/kotlin/com/example/one/ContractOne.kt")
            .withSubResource("cpk-final/build.gradle")
            .withSubResource("cpk-final/src/main/kotlin/com/example/final/ContractFinal.kt")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pannotations_version=$annotationsVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcorda_notary_plugin_version=$cordaNotaryPluginVersion",
                "-Pcpk1_version=$cpk1Version",
                "-PcpkFinal_version=$cpkFinalVersion"
            )
    }

    @Test
    fun deepTransitivesTest() {
        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cpb = artifacts.single { it.toString().endsWith(".cpb") }
        assertThat(cpb).isRegularFile

        val cpks = JarFile(cpb.toFile()).use { jar ->
            jar.entries().asSequence()
                .map(JarEntry::getName)
                .filter { it.endsWith(".jar") }
                .toList()
        }
        println(cpks)
        assertThat(cpks)
            .anyMatch { it == "notary-plugin-non-validating-client-$cordaNotaryPluginVersion.jar" }
            .anyMatch { it == "notary-plugin-non-validating-api-$cordaNotaryPluginVersion.jar" }
            .anyMatch { it == "notary-plugin-common-$cordaNotaryPluginVersion.jar" }
            .anyMatch { it == "cpk-final-$cpkFinalVersion.jar" }
            .anyMatch { it == "cpk-one-$cpk1Version.jar" }
            .hasSize(5)
    }
}
