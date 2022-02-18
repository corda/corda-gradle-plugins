package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE.JAVA_17
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@EnabledForJreRange(min = JAVA_17)
@TestInstance(PER_CLASS)
class RemoveAnnotationFromJavaRecordTest {
    private companion object {
        private const val ANNOTATION_CLASS = "net.corda.gradle.RemoveFromRecord"
        private const val RECORD_CLASS = "net.corda.gradle.RecordWithUnwantedAnnotation"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "remove-java-record-annotation").build()
    }

    fun ClassLoader.loadAnnotation(className: String): Class<out Annotation> {
        return load<Annotation>(className).apply {
            assertTrue(isAnnotation)
        }
    }

    @Test
    fun removeRecordAnnotations() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val annotation = cl.loadAnnotation(ANNOTATION_CLASS)
            cl.load<Any>(RECORD_CLASS).apply {
                assertTrue(isAnnotationPresent(annotation))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val annotation = cl.loadAnnotation(ANNOTATION_CLASS)
            cl.load<Any>(RECORD_CLASS).apply {
                assertFalse(isAnnotationPresent(annotation))
            }
        }
    }
}
