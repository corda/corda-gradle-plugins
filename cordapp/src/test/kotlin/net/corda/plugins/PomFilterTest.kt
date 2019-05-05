package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Document
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class PomFilterTest {
    private lateinit var testProject: GradleProject

    @BeforeEach
    fun setup(@TempDir testProjectDir: Path, reporter: TestReporter) {
        testProject = GradleProject(testProjectDir, reporter)
            .withTaskName("generatePomFileForCordappPublishPublication")
            .withBuildScript("""
                |plugins {
                |    id 'net.corda.plugins.cordapp'
                |    id 'maven-publish'
                |}
                |
                |apply from: 'repositories.gradle'
                |
                |version = '1.0-SNAPSHOT'
                |group = 'com.example'
                |
                |dependencies {
                |    compile "org.slf4j:jcl-over-slf4j:1.7.26"
                |    implementation "org.slf4j:slf4j-api:1.7.26"
                |    runtimeOnly "org.slf4j:slf4j-simple:1.7.26"
                |    cordaCompile "com.google.guava:guava:20.0"
                |    cordaRuntime "javax.servlet:javax.servlet-api:3.1.0"
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
            .build()
    }

    @Test
    fun testFilteredPom() {
        val pomFile = testProject.pathOf("build", "publications", "cordappPublish", "pom-default.xml")
        assertThat(pomFile).isRegularFile()

        val pom = pomFile.readXml()
        assertEquals(1, pom.getElementsByTagName("project").length, "Should contain <project/> tag")
        assertEquals(1, pom.getElementsByTagName("licenses").length, "Should contain <licenses/> tag")
        assertEquals(0, pom.getElementsByTagName("dependencies").length, "Should not contain <dependencies/> tag")
        assertEquals(0, pom.getElementsByTagName("dependency").length, "Should not contain <dependency/> tags")
    }

    private fun Path.readXml(): Document {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(toFile())
    }
}