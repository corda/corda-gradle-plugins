package net.corda.gradle.jarfilter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE.JAVA_17
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@EnabledForJreRange(min = JAVA_17)
@TestInstance(PER_CLASS)
class DeleteJavaRecordTest {
    private companion object {
        private const val UNWANTED_CLASS = "net.corda.gradle.UnwantedRecord"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-java-record").build()
    }

    @Test
    fun deleteJavaRecord() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(UNWANTED_CLASS)
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_CLASS) }
        }
    }
}
