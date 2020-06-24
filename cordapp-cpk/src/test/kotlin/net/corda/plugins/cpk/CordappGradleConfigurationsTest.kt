package net.corda.plugins.cpk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.stream.Collectors.toList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class CordappGradleConfigurationsTest {
    companion object {
        private lateinit var testProject: GradleProject
        private lateinit var dependencies: List<ZipEntry>

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withBuildScript("""\
                    |plugins {
                    |    id 'net.corda.plugins.cordapp-cpk'
                    |}
                    |
                    |apply from: 'repositories.gradle'
                    |
                    |version = '1.0-SNAPSHOT'
                    |group = 'com.example'
                    |
                    |dependencies {
                    |    compile "javax.activation:javax.activation-api:1.2.0"
                    |    runtime "commons-io:commons-io:2.7"
                    |    cordaImplementation "com.google.guava:guava:20.0"
                    |    cordaRuntime "javax.servlet:javax.servlet-api:3.1.0"
                    |    implementation "javax.persistence:javax.persistence-api:2.2"
                    |    runtimeOnly "javax.validation:validation-api:1.1.0.Final"
                    |}
                    |
                    |jar {
                    |    archiveBaseName = 'configurations'
                    |}
                    |
                    |cordapp {
                    |    contract {
                    |        name = 'Testing'
                    |        versionId = 1
                    |        targetPlatformVersion = 999
                    |    }
                    |}
                """.trimMargin())
                .build()

            val cordapp = testProject.artifacts.single { it.toString().endsWith(".cpk") }
            assertThat(cordapp).isRegularFile()

            dependencies = ZipFile(cordapp.toFile()).use { zip ->
                zip.stream().filter { entry -> entry.name.startsWith("lib/") && !entry.isDirectory }
                   .collect(toList())
            }
        }
    }

    @Test
    fun testCorrectNumberOfIncludes() {
        assertThat(dependencies).hasSize(4)
    }

    @Test
    fun testCompileIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: javax.activation-api-1.2.0.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "lib/javax.activation-api-1.2.0.jar" }
    }

    @Test
    fun testRuntimeIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: commons-io-2.7.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "lib/commons-io-2.7.jar" }
    }

    @Test
    fun testImplementationIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: javax.persistence-api-2.2.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "lib/javax.persistence-api-2.2.jar" }
    }

    @Test
    fun testRuntimeOnlyIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: validation-api-1.1.0.Final.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "lib/validation-api-1.1.0.Final.jar" }
    }

    @Test
    fun testCordaRuntimeExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: javax.servlet-api-3.1.0.jar")
        assertThat(dependencies)
            .noneMatch { it.name == "lib/javax.servlet-api-3.1.0.jar" }
    }

    @Test
    fun testCordaImplementationExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: guava-20.0.jar")
        assertThat(dependencies)
            .noneMatch { it.name == "lib/guava-20.0.jar" }
    }
}
