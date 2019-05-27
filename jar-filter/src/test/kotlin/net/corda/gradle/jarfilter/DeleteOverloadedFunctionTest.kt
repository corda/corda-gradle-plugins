package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isFunction
import org.assertj.core.api.Assertions.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

class DeleteOverloadedFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.HasOverloadedFunction"
        private const val LAMBDA_CLASS = "net.corda.gradle.HasOverloadWithLambda"

        private val stringData1 = isFunction("stringData", String::class, String::class)
        private val stringData2 = isFunction("stringData", String::class, Int::class, String::class)
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-overloaded-function").build()
        }
    }

    @Test
    fun deleteFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<Any>(FUNCTION_CLASS)) {
                newInstance().also {
                    assertEquals(MESSAGE, getDeclaredMethod("stringData", String::class.java).invoke(it, MESSAGE))
                    assertEquals("$NUMBER: $MESSAGE",
                        getDeclaredMethod("stringData", Int::class.java, String::class.java).invoke(it, NUMBER, MESSAGE))
                }
                assertThat("stringData(String) not found", kotlin.declaredFunctions, hasItem(stringData1))
                assertThat("stringData(Integer,String) not found", kotlin.declaredFunctions, hasItem(stringData2))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<Any>(FUNCTION_CLASS)) {
                newInstance().also {
                    assertFailsWith<NoSuchMethodException> { getDeclaredMethod("stringData", String::class.java) }
                    assertEquals("$NUMBER: $MESSAGE",
                        getDeclaredMethod("stringData", Int::class.java, String::class.java).invoke(it, NUMBER, MESSAGE))
                }
                assertThat("stringData(String) still exists", kotlin.declaredFunctions, not(hasItem(stringData1)))
                assertThat("stringData(Integer,String) not found", kotlin.declaredFunctions, hasItem(stringData2))
            }
        }
    }

    @Test
    fun deleteFunctionWithLambda() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<Any>(LAMBDA_CLASS)) {
                newInstance().also {
                    assertEquals("[$MESSAGE]", getDeclaredMethod("lambdaData", String::class.java).invoke(it, MESSAGE))
                    assertEquals("($NUMBER)", getDeclaredMethod("lambdaData", Int::class.java).invoke(it, NUMBER))
                }
                assertThat(kotlin.declaredFunctions).hasSize(2)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<Any>(LAMBDA_CLASS)) {
                newInstance().also {
                    assertFailsWith<NoSuchMethodException> { getDeclaredMethod("lambdaData", String::class.java) }
                    assertEquals("($NUMBER)", getDeclaredMethod("lambdaData", Int::class.java).invoke(it, NUMBER))
                }
                assertThat(kotlin.declaredFunctions).hasSize(1)
            }
        }
    }
}