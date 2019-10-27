package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
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
        private lateinit var poms: List<ZipEntry>

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withBuildScript("""
                    |plugins {
                    |    id 'net.corda.plugins.cordapp'
                    |}
                    |
                    |apply from: 'repositories.gradle'
                    |
                    |version = '1.0-SNAPSHOT'
                    |group = 'com.example'
                    |
                    |dependencies {
                    |    compile "org.slf4j:slf4j-api:1.7.26"
                    |    runtime "commons-io:commons-io:2.6"
                    |    cordaCompile "com.google.guava:guava:20.0"
                    |    cordaRuntime "javax.servlet:javax.servlet-api:3.1.0"
                    |    implementation "javax.persistence:javax.persistence-api:2.2"
                    |    runtimeOnly "javax.validation:validation-api:1.1.0.Final"
                    |}
                    |
                    |jar {
                    |    archiveName = 'configurations.jar'
                    |}
                    |
                    |cordapp {
                    |    info {
                    |        name = 'Testing'
                    |        targetPlatformVersion = 5
                    |    }
                    |}
                """.trimMargin())
                .build()

            val cordapp = testProject.pathOf("build", "libs", "configurations.jar")
            assertThat(cordapp).isRegularFile()

            poms = ZipFile(cordapp.toFile()).stream()
                .filter { entry -> entry.name.endsWith("/pom.xml") }
                .collect(toList())
        }
    }

    @Test
    fun testCorrectNumberOfIncludes() {
        assertEquals(2, poms.size)
    }

    @Test
    fun testCompileIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: slf4j-api-1.7.26.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/org.slf4j/slf4j-api/pom.xml" }
    }

    @Test
    fun testRuntimeIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: commons-io-2.6.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/commons-io/commons-io/pom.xml" }
    }

    @Test
    fun testImplementationExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: javax.persistence-api-2.2.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/javax.persistence/javax.persistence-api/pom.xml" }
    }

    @Test
    fun testRuntimeOnlyExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: validation-api-1.1.0.Final.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/javax.validation/validation-api/pom.xml" }
    }

    @Test
    fun testCordaRuntimeExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: javax.servlet-api-3.1.0.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/javax.servlet/javax.servlet-api/pom.xml" }
    }

    @Test
    fun testCordaCompileExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: guava-20.0.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/com.google.guava/guava/pom.xml" }
    }
}
