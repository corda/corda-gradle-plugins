package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import java.util.stream.Collectors.toList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class CordappGradleConfigurationsTest {
    companion object {
        private val testProjectDir = TemporaryFolder()
        private val testProject = GradleProject(testProjectDir)
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
            |    runtime "org.slf4j:slf4j-simple:1.7.26"
            |    cordaCompile "com.google.guava:guava:20.0"
            |    cordaRuntime "javax.servlet:javax.servlet-api:3.1.0"
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

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)

        private lateinit var poms: List<ZipEntry>

        @BeforeClass
        @JvmStatic
        fun checkSetup() {
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
            .contains("CorDapp dependency: slf4j-simple-1.7.26.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/org.slf4j/slf4j-simple/pom.xml" }
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
