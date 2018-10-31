package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isFunction
import org.assertj.core.api.Assertions.*
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

class DeleteOverloadedFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.HasOverloadedFunction"
        private const val LAMBDA_CLASS = "net.corda.gradle.HasOverloadWithLambda"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-overloaded-function")
        private val stringData1 = isFunction("stringData", String::class, String::class)
        private val stringData2 = isFunction("stringData", String::class, Int::class, String::class)

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
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