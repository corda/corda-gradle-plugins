package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.w3c.dom.Document
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class PomFilterTest {
    private val testProjectDir = TemporaryFolder()
    private val testProject = GradleProject(testProjectDir)
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

    @Rule
    @JvmField
    val rules: TestRule = RuleChain
        .outerRule(testProjectDir)
        .around(testProject)

    @Test
    fun testFilteredPom() {
        val pomFile = testProject.pathOf("build", "publications", "cordappPublish", "pom-default.xml")
        assertThat(pomFile).isRegularFile()

        val pom = pomFile.readXml()
        assertEquals("Should contain <project/> tag", 1, pom.getElementsByTagName("project").length)
        assertEquals("Should contain <licenses/> tag", 1, pom.getElementsByTagName("licenses").length)
        assertEquals("Should not contain <dependencies/> tag", 0, pom.getElementsByTagName("dependencies").length)
        assertEquals("Should not contain <dependency/> tags", 0, pom.getElementsByTagName("dependency").length)
    }

    private fun Path.readXml(): Document {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(toFile())
    }
}