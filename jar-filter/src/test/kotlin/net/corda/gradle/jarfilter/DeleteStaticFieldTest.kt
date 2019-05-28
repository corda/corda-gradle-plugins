package net.corda.gradle.jarfilter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeleteStaticFieldTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.StaticFieldsToDelete"
        private const val DEFAULT_BIG_NUMBER: Long = 123456789L
        private const val DEFAULT_NUMBER: Int = 123456

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-static-field").build()
        }
    }

    @Test
    fun deleteStringField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredField("stringField")
                assertEquals(DEFAULT_MESSAGE, getter.get(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchFieldException> { getDeclaredField("stringField") }
            }
        }
    }

    @Test
    fun deleteLongField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredField("longField")
                assertEquals(DEFAULT_BIG_NUMBER, getter.get(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchFieldException> { getDeclaredField("longField") }
            }
        }
    }

    @Test
    fun deleteIntField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredField("intField")
                assertEquals(DEFAULT_NUMBER, getter.get(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchFieldException> { getDeclaredField("intField") }
            }
        }
    }
}