package net.corda.plugins.cpb2

import net.corda.plugins.cpk2.GradleProject
import net.corda.plugins.cpk2.cordaApiVersion
import net.corda.plugins.cpk2.expectedCordappContractVersion
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@TestInstance(PER_CLASS)
class CpbSharedName {
    private companion object {
        private const val platformCordappVersion = "2.3.4"
        private const val cordappVersion = "1.2.1"
    }

    private lateinit var externalProject: GradleProject

    @Test
    fun `Assert 2 CPKs don't share a cordappName`(
        @TempDir testDir: Path,
        reporter: TestReporter
    ) {
        val mavenRepoDir = Files.createDirectory(testDir.resolve("maven"))
        val cpbProjectDir = Files.createDirectory(testDir.resolve("cpb"))
        val e = assertThrows<UnexpectedBuildFailure> {
            GradleProject(cpbProjectDir, reporter)
                .withTestName("cpb-shared-name")
                .withSubResource("project-dependency/build.gradle")
                .withSubResource("second-project-dependency/build.gradle")
                .build(
                    "-Pmaven_repository_dir=$mavenRepoDir",
                    "-Pplatform_cordapp_version=$platformCordappVersion",
                    "-Pcordapp_version=$cordappVersion",
                    "-Pcordapp_contract_version=$expectedCordappContractVersion"
                )
        }

        val expectedMessage = "Two CPKs may not share a cordappName."
        assert(e.message!!.contains(expectedMessage)) {
            "Error message does not match expected value. Error message should contain \"$expectedMessage\""
        }
    }
}
