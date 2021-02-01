package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isFunction
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Modifier.ABSTRACT
import java.nio.file.Path
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

class AbstractFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.AbstractFunctions"

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "abstract-function").build()
        }
    }

    @Test
    fun deleteAbstractFunction() {
        val longFunction = isFunction("toDelete", Long::class, Long::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toDelete", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
                assertThat("toDelete(J) not found", kotlin.declaredFunctions, hasItem(longFunction))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("toDelete", Long::class.java) }
                assertThat("toDelete(J) still exists", kotlin.declaredFunctions, not(hasItem(longFunction)))
            }
        }
    }

    @Test
    fun cannotStubAbstractFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toStubOut", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toStubOut", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }
    }
}
