package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.Calendar.FEBRUARY
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.fail

class JarFilterTimestampTest {
    companion object {
        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.timeInMillis
        )

        private lateinit var sourceJar: DummyJar
        private lateinit var filteredJar: Path
        private lateinit var output: String

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            sourceJar = DummyJar(testProjectDir, JarFilterTimestampTest::class.java, "timestamps").build()
            filteredJar = createTestProject(testProjectDir, sourceJar.path.toUri())
        }

        private fun createTestProject(testProjectDir: Path, source: URI): Path {
            testProjectDir.installResources("gradle.properties", "settings.gradle")
            testProjectDir.resolve("build.gradle").toFile().writeText("""
                |plugins {
                |    id 'net.corda.plugins.jar-filter'
                |}
                |
                |import net.corda.gradle.jarfilter.JarFilterTask
                |task jarFilter(type: JarFilterTask) {
                |    jars file("$source")
                |    preserveTimestamps = false
                |}
                |""".trimMargin())
            val result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(getGradleArgsForTasks("jarFilter"))
                .withPluginClasspath()
                .build()
            output = result.output
            println(output)

            val jarFilter = result.task(":jarFilter") ?: fail("No outcome for jarFilter task")
            assertEquals(SUCCESS, jarFilter.outcome)

            val filtered = testProjectDir.pathOf("build", "filtered-libs", "timestamps-filtered.jar")
            assertThat(filtered).isRegularFile()
            return filtered
        }

        private val ZipEntry.methodName: String get() = if (method == ZipEntry.STORED) "Stored" else "Deflated"
    }

    @Test
    fun fileTimestampsAreRemoved() {
        var directoryCount = 0
        var classCount = 0
        var otherCount = 0

        ZipFile(filteredJar.toFile()).use { jar ->
            for (entry in jar.entries()) {
                println("Entry: ${entry.name}")
                println("- ${entry.methodName} (${entry.size} size / ${entry.compressedSize} compressed) bytes")
                assertThat(entry.lastModifiedTime).isEqualTo(CONSTANT_TIME)
                assertThat(entry.lastAccessTime).isNull()
                assertThat(entry.creationTime).isNull()

                when {
                    entry.isDirectory -> ++directoryCount
                    entry.name.endsWith(".class") -> ++classCount
                    else -> ++otherCount
                }
            }
        }

        assertThat(directoryCount).isGreaterThan(0)
        assertThat(classCount).isGreaterThan(0)
        assertThat(otherCount).isGreaterThan(0)
    }
}