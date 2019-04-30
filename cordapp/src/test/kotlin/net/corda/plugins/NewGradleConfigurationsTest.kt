package net.corda.plugins

import org.assertj.core.api.Assertions.*
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

class NewGradleConfigurationsTest {
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
            |    implementation "org.slf4j:slf4j-api:1.7.26"
            |    runtimeOnly "org.slf4j:slf4j-simple:1.7.26"
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
    fun testImplementationIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: slf4j-api-1.7.26.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/org.slf4j/slf4j-api/pom.xml" }
    }

    @Test
    fun testRuntimeOnlyIncluded() {
        assertThat(testProject.output)
            .contains("CorDapp dependency: slf4j-simple-1.7.26.jar")
        assertThat(poms)
            .anyMatch { it.name == "META-INF/maven/org.slf4j/slf4j-simple/pom.xml" }
    }
}