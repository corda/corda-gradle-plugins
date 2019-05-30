package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasString
import net.corda.gradle.unwanted.HasUnwantedFun
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.jvm.kotlin
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

class DeleteFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.HasFunctionToDelete"
        private const val INDIRECT_CLASS = "net.corda.gradle.HasIndirectFunctionToDelete"

        private val unwantedFun = isFunction("unwantedFun", String::class, String::class)
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-function").build()
        }
    }

    @Test
    fun deleteFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also {
                    assertEquals(MESSAGE, it.unwantedFun(MESSAGE))
                }
                assertThat("unwantedFun(String) not found", kotlin.declaredFunctions, hasItem(unwantedFun))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also {
                    assertFailsWith<AbstractMethodError> { it.unwantedFun(MESSAGE) }
                }
                assertThat("unwantedFun(String) still exists", kotlin.declaredFunctions, not(hasItem(unwantedFun)))
            }
        }
    }

    @Test
    fun deleteIndirectFunction() {
        val stringData = isFunction("stringData", String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedFun>(INDIRECT_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.unwantedFun(MESSAGE))
                    assertEquals(MESSAGE, (it as HasString).stringData())
                }
                assertThat("unwantedFun(String) not found", kotlin.declaredFunctions, hasItem(unwantedFun))
                assertThat("stringData() not found", kotlin.declaredFunctions, hasItem(stringData))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedFun>(INDIRECT_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertFailsWith<AbstractMethodError> { it.unwantedFun(MESSAGE) }
                    assertFailsWith<AbstractMethodError> { (it as HasString).stringData() }
                }
                assertThat("unwantedFun(String) still exists", kotlin.declaredFunctions, not(hasItem(unwantedFun)))
                assertThat("stringData still exists", kotlin.declaredFunctions, not(hasItem(stringData)))
            }
        }
    }
}
