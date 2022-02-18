package net.corda.gradle.jarfilter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteStaticValPropertyTest {
    private companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.StaticValToDelete"
        private const val DEFAULT_BIG_NUMBER: Long = 123456789L
        private const val DEFAULT_NUMBER: Int = 123456

        private object LocalBlob
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-static-val").build()
    }

    @Test
    fun deleteStringVal() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getStringVal")
                assertEquals(DEFAULT_MESSAGE, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getStringVal") }
            }
        }
    }

    @Test
    fun deleteLongVal() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getLongVal")
                assertEquals(DEFAULT_BIG_NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getLongVal") }
            }
        }
    }

    @Test
    fun deleteIntVal() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getIntVal")
                assertEquals(DEFAULT_NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getIntVal") }
            }
        }
    }

    @Test
    fun deleteMemberVal() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getMemberVal", Any::class.java)
                assertEquals(LocalBlob, getter.invoke(null, LocalBlob))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getMemberVal", Any::class.java) }
            }
        }
    }
}