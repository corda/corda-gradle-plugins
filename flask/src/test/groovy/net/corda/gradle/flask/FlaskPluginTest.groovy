package net.corda.gradle.flask


import groovy.transform.CompileStatic
import net.corda.flask.common.Flask
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import static net.corda.flask.common.Flask.Constants.BUFFER_SIZE
import static net.corda.flask.common.Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotEquals
import static org.junit.jupiter.api.Assertions.assertTrue
import static java.util.concurrent.TimeUnit.MINUTES

@CompileStatic
@Timeout(value = 3L, unit = MINUTES)
class FlaskPluginTest {
    private static final int EOF = -1

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

    private final String testGradleHomeDir = System.getProperty("test.gradle.user.home")

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
        installResource("testProject","src/main/java/net/corda/gradle/flask/test/HangingMain.java", testProjectDir)
        installResource("testProject","src/flask/java/net/corda/gradle/flask/test/TestLauncher.java", testProjectDir)
        installResource("testProject", "testAgent/build.gradle", testProjectDir)
        installResource("testProject", "testAgent/src/main/java/net/corda/gradle/flask/test/agent/JavaAgent.java", testProjectDir)
    }

    void invokeGradle(String... taskName) {
        GradleRunner runner = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName + ["-s", "--info", "-g", testGradleHomeDir])
                .withPluginClasspath()
        println(runner.build().getOutput())
    }

    static long consumeEntry(InputStream input) {
        byte[] buffer = new byte[BUFFER_SIZE]
        long totalBytes = 0
        long bytesRead
        while ((bytesRead = input.read(buffer)) != EOF) {
            totalBytes += bytesRead
        }
        return totalBytes
    }

    @Test
    void buildFlaskJar() {
        invokeGradle("flaskJar")

        //Check that all zip entries have timestamp equal to Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP,
        //that directories and jars have been STORED, and everything else has been DEFLATED.
        Path flaskJar = testProjectDir.resolve("build/flask.jar")
        new ZipInputStream(Files.newInputStream(flaskJar)).withStream { zipInputStream ->
            ZipEntry zipEntry
            while((zipEntry = zipInputStream.nextEntry) != null) {
                assertEquals(ZIP_ENTRIES_DEFAULT_TIMESTAMP, zipEntry.time)

                // A DEFLATED zipEntry is not fully populated until its stream has been consumed.
                long actualSize = consumeEntry(zipInputStream)
                assertThat(zipEntry.size).isEqualTo(actualSize)
                if (zipEntry.isDirectory()) {
                    assertEquals(ZipEntry.STORED, zipEntry.method, zipEntry.name)
                    assertThat(zipEntry.compressedSize).isZero()
                    assertThat(zipEntry.size).isZero()
                    assertThat(zipEntry.crc).isZero()
                } else if (zipEntry.name.endsWith(".jar")) {
                    assertEquals(ZipEntry.STORED, zipEntry.method, zipEntry.name)
                    assertThat(zipEntry.compressedSize).isEqualTo(actualSize)
                    assertThat(zipEntry.crc).isNotZero()
                } else {
                    assertEquals(ZipEntry.DEFLATED, zipEntry.method, zipEntry.name)
                    assertThat(zipEntry.compressedSize).isLessThan(actualSize).isPositive()
                    assertThat(zipEntry.crc).isNotZero()
                }
            }
        }
    }

    @Test
    void compareFlaskJars() {
        invokeGradle("flaskJar")
        Path flaskJar = testProjectDir.resolve("build/flask.jar")
        byte[] buffer = new byte[BUFFER_SIZE]
        MessageDigest md = MessageDigest.getInstance("SHA-256")
        byte[] digest1 = Flask.computeDigest( { Files.newInputStream(flaskJar) }, md, buffer)

        invokeGradle("clean", "flaskJar")
        md.reset()
        byte[] digest2 = Flask.computeDigest( { Files.newInputStream(flaskJar) }, md, buffer)
        //Recreating the flask jar archive from the same project setting "preserveFileTimestamps = false"
        // and "reproducibleFileOrder = true" on all included artifacts should always result in the same byte sequence
        assertArrayEquals(digest1, digest2)
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
        assertEquals("net.corda.gradle.flask.test.Main", prop.mainClassName)
        assertEquals("arg1 arg2 arg3", prop.args)
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
        assertEquals("net.corda.gradle.flask.test.AlternativeMain", prop.mainClassName)
    }

    @Test
    void runWithDisabledJavaAgent() {
        invokeGradle("flaskRunDisableJavaAgent")
    }

    @Test
    @DisplayName("Check that shutdown hooks are executed on flask jars")
    void shutdownHookTest() {
        invokeGradle("shutdownHookTestJar")
        String javaHome = System.getProperty("java.home")
        String javaExecutable = javaHome + File.separator + "bin" + File.separator + "java"
        Path jarFile = testProjectDir.resolve("build/libs/shutdownHookTest.jar")
        Process process = new ProcessBuilder(Arrays.asList(javaExecutable, '-jar', jarFile.toString()))
            .directory(testProjectDir.toFile())
            .inheritIO()
            .start()
        Path testFile = testProjectDir.resolve("shutdown-hook-executed")
        int totalWait = 0
        final int timeoutMillis = 5000
        while(!Files.exists(testFile)) {
            int loopWait = 100
            totalWait += loopWait
            assertTrue(totalWait < timeoutMillis, "Failed to create test file '$testFile'")
            Thread.sleep(loopWait)
        }
        process.destroy()
        int returnCode = process.waitFor()
        assertNotEquals(0, returnCode)
        assertThat(testProjectDir.resolve("shutdown-hook-executed")).doesNotExist()
    }
}
