package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.test.*

class StubStaticFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.StaticFunctionsToStub"

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "stub-static-function").build()
        }
    }

    @Test
    fun stubStringFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedStringToStub", String::class.java).also { method ->
                    method.invoke(null, MESSAGE).also { result ->
                        assertThat(result)
                            .isInstanceOf(String::class.java)
                            .isEqualTo(MESSAGE)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedStringToStub", String::class.java).also { method ->
                    assertFailsWith<InvocationTargetException> { method.invoke(null, MESSAGE) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubLongFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedLongToStub", Long::class.java).also { method ->
                    method.invoke(null, BIG_NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Long::class.javaObjectType)
                            .isEqualTo(BIG_NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedLongToStub", Long::class.java).also { method ->
                    assertFailsWith<InvocationTargetException> { method.invoke(null, BIG_NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubIntFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedIntToStub", Int::class.java).also { method ->
                    method.invoke(null, NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Int::class.javaObjectType)
                            .isEqualTo(NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getDeclaredMethod("unwantedIntToStub", Int::class.java).also { method ->
                    assertFailsWith<InvocationTargetException> { method.invoke(null, NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubVoidFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                val staticSeed = getDeclaredMethod("getStaticSeed")
                assertEquals(0, staticSeed.invoke(null))
                getDeclaredMethod("unwantedVoidToStub").invoke(null)
                assertEquals(1, staticSeed.invoke(null))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                val staticSeed = getDeclaredMethod("getStaticSeed")
                assertEquals(0, staticSeed.invoke(null))
                getDeclaredMethod("unwantedVoidToStub").invoke(null)
                assertEquals(0, staticSeed.invoke(null))
            }
        }
    }
}
