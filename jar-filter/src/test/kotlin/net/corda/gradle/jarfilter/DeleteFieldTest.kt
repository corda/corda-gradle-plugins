package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isProperty
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteFieldTest {
    private companion object {
        private const val STRING_FIELD_CLASS = "net.corda.gradle.HasStringFieldToDelete"
        private const val INTEGER_FIELD_CLASS = "net.corda.gradle.HasIntFieldToDelete"
        private const val LONG_FIELD_CLASS = "net.corda.gradle.HasLongFieldToDelete"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-field").build()
    }

    @Test
    fun deleteStringField() {
        val stringField = isProperty("stringField", String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(STRING_FIELD_CLASS).apply {
                val obj: Any = getDeclaredConstructor(String::class.java).newInstance(MESSAGE)
                getDeclaredField("stringField").also { field ->
                    assertEquals(MESSAGE, field.get(obj))
                }
                assertThat("stringField not found", kotlin.declaredMemberProperties, hasItem(stringField))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(STRING_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("stringField") }
                assertThat("stringField still exists", kotlin.declaredMemberProperties, not(hasItem(stringField)))
            }
        }
    }

    @Test
    fun deleteLongField() {
        val longField = isProperty("longField", Long::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(LONG_FIELD_CLASS).apply {
                val obj: Any = getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
                getDeclaredField("longField").also { field ->
                    assertEquals(BIG_NUMBER, field.get(obj))
                }
                assertThat("longField not found", kotlin.declaredMemberProperties, hasItem(longField))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(LONG_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("longField") }
                assertThat("longField still exists", kotlin.declaredMemberProperties, not(hasItem(longField)))
            }
        }
    }

    @Test
    fun deleteIntegerField() {
        val intField = isProperty("intField", Int::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(INTEGER_FIELD_CLASS).apply {
                val obj: Any = getDeclaredConstructor(Int::class.java).newInstance(NUMBER)
                getDeclaredField("intField").also { field ->
                    assertEquals(NUMBER, field.get(obj))
                }
                assertThat("intField not found", kotlin.declaredMemberProperties, hasItem(intField))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(INTEGER_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(Int::class.java).newInstance(NUMBER))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("intField") }
                assertThat("intField still exists", kotlin.declaredMemberProperties, not(hasItem(intField)))
            }
        }
    }
}
