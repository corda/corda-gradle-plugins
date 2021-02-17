package net.corda.gradle.flask

import groovy.transform.CompileStatic
import net.corda.flask.common.Flask
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.api.io.TempDir

import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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
        installResource("testProject","src/main/java/net/corda/gradle/flask/test/AlternativeMain.java", testProjectDir)
        installResource("testProject","src/flask/java/net/corda/gradle/flask/test/TestLauncher.java", testProjectDir)
        installResource("testProject", "testAgent/build.gradle", testProjectDir)
        installResource("testProject", "testAgent/src/main/java/net/corda/gradle/flask/test/agent/JavaAgent.java", testProjectDir)
    }

    void invokeGradle(String... taskName) {
        GradleRunner runner = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName + ["-s", "--info", "-g", testGradleHomeDir.toString()])
                .withPluginClasspath()
        println(runner.build().getOutput())
    }

    @Test
    void buildFlaskJar() {
        invokeGradle("flaskJar")

        //Check that all zip entries have timestamp equal to Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP
        Path flaskJar = testProjectDir.resolve("build/flask.jar")
        new ZipInputStream(Files.newInputStream(flaskJar)).withStream { zipInputStream ->
            ZipEntry zipEntry
            while((zipEntry = zipInputStream.nextEntry) != null) {
                Assertions.assertEquals(Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP, zipEntry.time)
            }
        }
    }

    @Test
    void compareFlaskJars() {
        invokeGradle("flaskJar")
        Path flaskJar = testProjectDir.resolve("build/flask.jar")
        byte[] buffer = new byte[Flask.Constants.BUFFER_SIZE]
        MessageDigest md = MessageDigest.getInstance("SHA-256")
        byte[] digest1 = Flask.computeDigest( { Files.newInputStream(flaskJar) }, md, buffer)

        invokeGradle("clean", "flaskJar")
        md.reset()
        byte[] digest2 = Flask.computeDigest( { Files.newInputStream(flaskJar) }, md, buffer)
        //Recreating the flask jar archive from the same project setting "preserveFileTimestamps = false"
        // and "reproducibleFileOrder = true" on all included artifacts should always result in the same byte sequence
        Assertions.assertArrayEquals(digest1, digest2)
    }

    @Test
    void runFlaskJar() {
        invokeGradle("flaskRun")
        Path propertiesFile = testProjectDir.resolve("build/testLauncher.properties")
        Properties prop = Files.newBufferedReader(propertiesFile).withCloseable { reader ->
            new Properties().tap {
                load(reader)
            }
        }
        Assertions.assertEquals("net.corda.gradle.flask.test.Main", prop.mainClassName)
        Assertions.assertEquals("arg1 arg2 arg3", prop.args)
    }

    @Test
    void runFlaskJarMainClassOverride() {
        invokeGradle("flaskRunMainClassOverride")
        Path propertiesFile = testProjectDir.resolve("build/testLauncher.properties")
        Properties prop = Files.newBufferedReader(propertiesFile).withCloseable { reader ->
            new Properties().tap {
                load(reader)
            }
        }
        Assertions.assertEquals("net.corda.gradle.flask.test.AlternativeMain", prop.mainClassName)
    }
}
