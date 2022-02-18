package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteStaticVarPropertyTest {
    private companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.StaticVarToDelete"
        private const val DEFAULT_BIG_NUMBER: Long = 123456789L
        private const val DEFAULT_NUMBER: Int = 123456

        private object LocalBlob
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-static-var").build()
    }

    @Test
    fun deleteStringVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getStringVar")
                val setter = getDeclaredMethod("setStringVar", String::class.java)
                assertEquals(DEFAULT_MESSAGE, getter.invoke(null))
                setter.invoke(null, MESSAGE)
                assertEquals(MESSAGE, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getStringVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setStringVar", String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getLongVar")
                val setter = getDeclaredMethod("setLongVar", Long::class.java)
                assertEquals(DEFAULT_BIG_NUMBER, getter.invoke(null))
                setter.invoke(null, BIG_NUMBER)
                assertEquals(BIG_NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getLongVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setLongVar", Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getIntVar")
                val setter = getDeclaredMethod("setIntVar", Int::class.java)
                assertEquals(DEFAULT_NUMBER, getter.invoke(null))
                setter.invoke(null, NUMBER)
                assertEquals(NUMBER, getter.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getIntVar") }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setIntVar", Int::class.java) }
            }
        }
    }

    @Test
    fun deleteMemberVar() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                val getter = getDeclaredMethod("getMemberVar", Any::class.java)
                val setter = getDeclaredMethod("setMemberVar", Any::class.java, Any::class.java)
                assertEquals(LocalBlob, getter.invoke(null, LocalBlob))
                setter.invoke(null, LocalBlob, LocalBlob)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("getMemberVar", Any::class.java) }
                assertFailsWith<NoSuchMethodException> { getDeclaredMethod("setMemberVar", Any::class.java, Any::class.java) }
            }
        }
    }
}
