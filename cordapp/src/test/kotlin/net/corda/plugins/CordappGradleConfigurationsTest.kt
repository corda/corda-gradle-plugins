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

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
            testProject = GradleProject(testProjectDir, reporter)
                .withBuildScript("""
                    |plugins {
                    |    id 'net.corda.plugins.cordapp'
                    |}
                    |
                    |version = '1.0-SNAPSHOT'
                    |group = 'com.example'
                    |
                    |dependencies {
                    |    api "commons-io:commons-io:2.8.0"
                    |    compileOnly "org.slf4j:slf4j-api:1.7.32"
                    |    cordapp "javax.annotation:javax.annotation-api:1.3.2"
                    |    cordaProvided "com.google.guava:guava:20.0"
                    |    cordaRuntimeOnly "javax.servlet:javax.servlet-api:3.1.0"
                    |    implementation "javax.persistence:javax.persistence-api:2.2"
                    |    runtimeOnly "javax.validation:validation-api:1.1.0.Final"
                    |}
                    |
                    |tasks.named('jar', Jar) {
                    |    archiveFileName = 'configurations.jar'
                    |}
                    |
                    |cordapp {
                    |    contract {
                    |        name = 'Testing'
                    |        versionId = 1
                    |        targetPlatformVersion = 5
                    |    }
                    |}
                """.trimMargin())
                .build()

            val cordapp = testProject.pathOf("build", "libs", "configurations.jar")
            assertThat(cordapp).isRegularFile

            poms = ZipFile(cordapp.toFile()).use { zip ->
                zip.stream().filter { entry -> entry.name.endsWith("/pom.xml") }
                   .collect(toList())
            }
        }
    }

    @Test
    fun testCorrectNumberOfIncludes() {
        assertEquals(3, poms.size)
    }

    @Test
    fun testCompileOnlyExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: slf4j-api-1.7.32.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/org.slf4j/slf4j-api/pom.xml" }
    }

    @Test
    fun testApiIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: commons-io-2.8.0.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/commons-io/commons-io/pom.xml" }
    }

    @Test
    fun testCordappExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: javax.annotation-api-1.3.2.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/javax.annotation/javax.annotation-api/pom.xml" }
    }

    @Test
    fun testImplementationIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: javax.persistence-api-2.2.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/javax.persistence/javax.persistence-api/pom.xml" }
    }

    @Test
    fun testRuntimeOnlyIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: validation-api-1.1.0.Final.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/javax.validation/validation-api/pom.xml" }
    }

    @Test
    fun testCordaRuntimeOnlyExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: javax.servlet-api-3.1.0.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/javax.servlet/javax.servlet-api/pom.xml" }
    }

    @Test
    fun testCordaProvidedExcluded() {
        assertThat(testProject.output)
            .doesNotContain("CorDapp dependency: guava-20.0.jar")
        assertThat(poms)
            .noneMatch { it.name == "META-INF/maven/com.google.guava/guava/pom.xml" }
    }
}
