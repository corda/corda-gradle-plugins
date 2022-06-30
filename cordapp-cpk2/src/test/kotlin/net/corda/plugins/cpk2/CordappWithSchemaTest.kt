package net.corda.plugins.cpk2

import aQute.bnd.osgi.Constants.PRIVATE_PACKAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.EXPORT_PACKAGE
import java.nio.file.Path

@TestInstance(PER_CLASS)
class CordappWithSchemaTest {
    private companion object {
        private const val cordappVersion = "1.0.1-SNAPSHOT"
        private const val slf4jVersion = "2.0.0-alpha1"
    }

    private lateinit var testProject: GradleProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTestName("cordapp-with-schema")
            .withSubResource("src/main/kotlin/com/example/schema/SchemaContract.kt")
            .withSubResource("src/main/resources/migration/sample.changelog-master.xml")
            .withSubResource("src/main/resources/migration/sample-migration-v1.0.xml")
            .build(
                "-Pcordapp_version=$cordappVersion",
                "-Pcordapp_contract_version=$expectedCordappContractVersion",
                "-Pcorda_api_version=$cordaApiVersion",
                "-Pslf4j_version=$slf4jVersion"
            )
    }

    @Test
    fun schemaMigrationTest() {
        val artifacts = testProject.artifacts
        assertThat(artifacts).hasSize(2)

        val cordapp = artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        val jarManifest = cordapp.manifest
        println(jarManifest.mainAttributes.entries)

        with(jarManifest.mainAttributes) {
            assertThatHeader(getValue(PRIVATE_PACKAGE))
                .containsPackageWithAttributes("migration")
            assertThatHeader(getValue(EXPORT_PACKAGE))
                .doesNotContainPackage("migration")
        }
    }
}
