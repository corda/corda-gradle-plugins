package net.corda.plugins.cpb

import net.corda.plugins.cpk.GradleProject
import net.corda.plugins.cpk.annotationsVersion
import net.corda.plugins.cpk.cordaApiVersion
import net.corda.plugins.cpk.expectedCordappContractVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

@TestInstance(PER_CLASS)
class DeepTransitiveCordappsTest {
    private companion object {
        private const val cpk1Version = "1.0-SNAPSHOT"
        private const val cpk2Version = "2.0-SNAPSHOT"
        private const val cpkFinalVersion = "3.0-SNAPSHOT"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("deep-transitive-cordapps")
            .withSubResource("cpk-one/build.gradle")
            .withSubResource("cpk-one/src/main/kotlin/com/example/one/ContractOne.kt")
            .withSubResource("cpk-two/build.gradle")
            .withSubResource("cpk-two/src/main/kotlin/com/example/one/ContractTwo.kt")
            .withSubResource("cpk-final/build.gradle")
            .withSubResource("cpk-final/src/main/kotlin/com/example/final/ContractFinal.kt")
            .build(
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pannotations_version=$annotationsVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pcpk1_version=$cpk1Version",
                "-Pcpk2_version=$cpk2Version",
                "-PcpkFinal_version=$cpkFinalVersion"
            )
    }

    @Test
    fun deepTransitivesTest() {
        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(1)

        val cpb = artifacts.single { it.toString().endsWith(".cpb") }
        assertThat(cpb).isRegularFile()

        val cpks = JarFile(cpb.toFile()).use { jar ->
            jar.entries().asSequence()
                .map(JarEntry::getName)
                .filter { it.endsWith(".cpk") }
                .toList()
        }
        assertThat(cpks)
            .anyMatch { it == "cpk-one-$cpk1Version-cordapp.cpk" }
            .anyMatch { it == "cpk-two-$cpk2Version-cordapp.cpk" }
            .anyMatch { it == "cpk-final-$cpkFinalVersion-cordapp.cpk" }
            .hasSize(3)
    }
}
