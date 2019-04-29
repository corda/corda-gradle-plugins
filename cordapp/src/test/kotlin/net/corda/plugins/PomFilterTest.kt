package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.fail
import org.w3c.dom.Document
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class PomFilterTest {
    private companion object {
        private val testGradleUserHome = systemProperty("test.gradle.user.home")
    }

    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Before
    fun setup() {
        installResource(testProjectDir, "settings.gradle")
        installResource(testProjectDir, "repositories.gradle")
    }

    @Test
    fun testFilteredPom() {
        testProjectDir.newFile("build.gradle").writeText("""
            |plugins {
            |    id 'java'
            |    id 'maven-publish'
            |    id 'net.corda.plugins.cordapp'
            |}
            |
            |version = '1.0-SNAPSHOT'
            |group = 'com.example'
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |dependencies {
            |    implementation "org.slf4j:slf4j-api:1.7.26"
            |    cordaCompile "com.google.guava:guava:20.0"
            |    cordaRuntime "org.slf4j:slf4j-simple:1.7.26"
            |}
            |
            |cordapp {
            |    info {
            |        name = 'Testing'
            |        targetPlatformVersion = 5
            |    }
            |}
            |
            |publishing {
            |    publications {
            |        cordappPublish(MavenPublication) {
            |            from components.java
            |
            |            groupId project.group
            |            artifactId 'cordapp-test'
            |
            |            pom {
            |                licenses {
            |                    license {
            |                        name = 'Apache-2.0'
            |                        url = 'https://www.apache.org/licenses/LICENSE-2.0'
            |                        distribution = 'repo'
            |                    }
            |                }
            |            }
            |        }
            |    }
            |}
        """.trimMargin())
        val result = jarTaskRunner(listOf("generatePomFileForCordappPublishPublication")).build()
        println(result.output)

        val generatePom = result.task(":generatePomFileForCordappPublishPublication")
            ?: fail("No outcome for generatePomFileForCordappPublishPublication task")
        assertEquals(SUCCESS, generatePom.outcome)

        val pomFile = Paths.get(testProjectDir.root.absolutePath, "build", "publications", "cordappPublish", "pom-default.xml")
        assertThat(pomFile).isRegularFile()

        val pom = pomFile.readXml()
        assertEquals("Should contain <project/> tag", 1, pom.getElementsByTagName("project").length)
        assertEquals("Should contain <licenses/> tag", 1, pom.getElementsByTagName("licenses").length)
        assertEquals("Should not contain <dependencies/> tag", 0, pom.getElementsByTagName("dependencies").length)
        assertEquals("Should not contain <dependency/> tags", 0, pom.getElementsByTagName("dependency").length)
    }

    private fun jarTaskRunner(extraArgs: List<String> = emptyList()): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(listOf("jar", "-s", "--info", "-g", testGradleUserHome) + extraArgs)
            .withPluginClasspath()
            .withDebug(true)
    }

    private fun Path.readXml(): Document {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(toFile())
    }
}