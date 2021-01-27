package net.corda.gradle.flask

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@CompileStatic
class FlaskPluginTest {

    private void installResource(String root, String resourceName, Path destination) {
        Path outputFile = with {
            Path realDestination
            if (Files.isSymbolicLink(destination)) {
                realDestination = destination.toRealPath()
            } else {
                realDestination = destination
            }
            realDestination = realDestination.resolve(resourceName)
            if(!Files.exists(realDestination)) {
                    Files.createDirectories(realDestination.parent)
                    realDestination
            } else if(Files.isDirectory(realDestination)) {
                realDestination.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
            }
            else if(Files.isRegularFile(realDestination)) {
                realDestination
            } else throw new IllegalStateException("Path '${realDestination}' is neither a file nor a directory")
        }
        String resourcePath = root + '/' + resourceName
        InputStream inputStream = (this.class.getResourceAsStream(resourcePath)
                ?: this.class.classLoader.getResourceAsStream(resourcePath))
        if(inputStream) {
            inputStream.withStream {
                Files.copy(it, outputFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            throw new FileNotFoundException(resourceName)
        }
    }

    @TempDir
    public Path testGradleHomeDir

    @TempDir
    public Path testProjectDir

    Path buildFile

    @BeforeEach
    void setup() {
        buildFile = testProjectDir.resolve("build.gradle")
        installResource("testProject", "build.gradle", testProjectDir)
        installResource("testProject", "settings.gradle", testProjectDir)
        installResource("testProject","src/main/java/net/corda/gradle/flask/test/Main.java", testProjectDir)
        installResource("testProject","src/flask/java/net/corda/gradle/flask/test/TestLauncher.java", testProjectDir)
        installResource("testProject", "testAgent/build.gradle", testProjectDir)
        installResource("testProject", "testAgent/src/main/java/net/corda/gradle/flask/test/agent/JavaAgent.java", testProjectDir)
    }

    GradleRunner getStandardGradleRunnerFor(String taskName) {
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName, "-s", "--info", "-g", testGradleHomeDir.toString())
                .withPluginClasspath()
    }

    @Test
    void buildFlaskJar() {
        GradleRunner runner = getStandardGradleRunnerFor("flaskJar")
        BuildResult result = runner.build()
    }

    @Test
    void runFlaskJar() {
        GradleRunner runner = getStandardGradleRunnerFor("flaskRun")
        BuildResult result = runner.build()
        Path propertiesFile = testProjectDir.resolve("build/testLauncher.properties")
        Properties prop = Files.newBufferedReader(propertiesFile).withCloseable { reader ->
            new Properties().tap {
                load(reader)
            }
        }
        Assertions.assertEquals("net.corda.gradle.flask.test.Main", prop.mainClassName)
        Assertions.assertEquals("arg1 arg2 arg3", prop.args)
    }
}
