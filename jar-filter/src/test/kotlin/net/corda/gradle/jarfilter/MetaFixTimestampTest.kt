package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Calendar.FEBRUARY
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.STORED
import java.util.zip.ZipFile

class MetaFixTimestampTest {
    companion object {
        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.timeInMillis
        )

        private lateinit var sourceJar: DummyJar
        private lateinit var metafixedJar: Path
        private lateinit var output: String

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            sourceJar = DummyJar(testProjectDir, MetaFixTimestampTest::class.java, "timestamps").build()
            metafixedJar = createTestProject(testProjectDir, sourceJar.path.toUri())
        }

        private fun createTestProject(testProjectDir: Path, source: URI): Path {
            testProjectDir.installResources("gradle.properties", "settings.gradle")
            testProjectDir.resolve("build.gradle").toFile().writeText("""
                |plugins {
                |    id 'net.corda.plugins.jar-filter'
                |}
                |
                |import net.corda.gradle.jarfilter.MetaFixerTask
                |task metafix(type: MetaFixerTask) {
                |    jars file("$source")
                |    preserveTimestamps = false
                |}
                |""".trimMargin())
            val result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(getGradleArgsForTasks("metafix"))
                .withPluginClasspath()
                .withDebug(true)
                .build()
            output = result.output
            println(output)

            val metafix = result.task(":metafix") ?: fail("No outcome for metafix task")
            assertEquals(SUCCESS, metafix.outcome)

            val metaFixed = testProjectDir.pathOf("build", "metafixer-libs", "timestamps-metafixed.jar")
            assertThat(metaFixed).isRegularFile
            return metaFixed
        }

        private val ZipEntry.methodName: String get() = if (method == STORED) "Stored" else "Deflated"
    }

    @Test
    fun fileTimestampsAreRemoved() {
        var directoryCount = 0
        var classCount = 0
        var otherCount = 0

        ZipFile(metafixedJar.toFile()).use { jar ->
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
