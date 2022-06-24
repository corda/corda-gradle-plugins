package net.corda.plugins.cpk2

import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.stream.Collectors.toList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@TestInstance(PER_CLASS)
class CordappGradleConfigurationsTest {
    private companion object {
        private val GRADLE_7_2 = GradleVersion.version("7.2")
    }

    private lateinit var testProject: GradleProject
    private lateinit var dependencies: List<ZipEntry>

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withGradleVersion(GRADLE_7_2)
            .withBuildScript("""\
                |plugins {
                |    id 'net.corda.plugins.cordapp-cpk2'
                |}
                |
                |apply from: 'repositories.gradle'
                |
                |version = '1.0-SNAPSHOT'
                |group = 'com.example'
                |
                |dependencies {
                |    cordaProvided "com.google.guava:guava:20.0"
                |    cordaRuntimeOnly "javax.servlet:javax.servlet-api:3.1.0"
                |    api "javax.annotation:javax.annotation-api:1.3.2"
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

        val cordapp = testProject.artifacts.single { it.toString().endsWith(".jar") }
        assertThat(cordapp).isRegularFile

        dependencies = ZipFile(cordapp.toFile()).use { zip ->
            zip.stream().filter { entry -> entry.name.startsWith("META-INF/privatelib/") && !entry.isDirectory }
               .collect(toList())
        }
    }

    @Test
    fun testCorrectNumberOfIncludes() {
        assertThat(dependencies).hasSize(3)
    }

    @Test
    fun testApiIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp library dependency: javax.annotation-api-1.3.2.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "META-INF/privatelib/javax.annotation-api-1.3.2.jar" }
    }

    @Test
    fun testImplementationIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp library dependency: javax.persistence-api-2.2.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "META-INF/privatelib/javax.persistence-api-2.2.jar" }
    }

    @Test
    fun testRuntimeOnlyIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp library dependency: validation-api-1.1.0.Final.jar")
        assertThat(dependencies)
            .anyMatch { it.name == "META-INF/privatelib/validation-api-1.1.0.Final.jar" }
    }

    @Test
    fun testCordaRuntimeOnlyExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp library dependency: javax.servlet-api-3.1.0.jar")
        assertThat(dependencies)
            .noneMatch { it.name == "META-INF/privatelib/javax.servlet-api-3.1.0.jar" }
    }

    @Test
    fun testCordaProvidedExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp library dependency: guava-20.0.jar")
        assertThat(dependencies)
            .noneMatch { it.name == "META-INF/privatelib/guava-20.0.jar" }
    }
}
