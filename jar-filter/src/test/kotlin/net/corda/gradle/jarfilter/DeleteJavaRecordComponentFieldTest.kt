package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.assertEquals
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
class DeleteJavaRecordComponentFieldTest {
    private companion object {
        private const val RECORD_CLASS = "net.corda.gradle.RecordWithUnwantedComponentField"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-java-record-component-field").build()
    }

    @Test
    fun deleteRecordComponentField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(RECORD_CLASS).apply {
                getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .newInstance(MESSAGE, NUMBER, BIG_NUMBER).also { obj ->
                        assertEquals(MESSAGE, getMethod("name").invoke(obj))
                        assertEquals(NUMBER, getMethod("number").invoke(obj))
                        assertEquals(BIG_NUMBER, getMethod("bigNumber").invoke(obj))
                    }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(RECORD_CLASS) }
        }
    }
}
