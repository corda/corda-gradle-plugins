package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedFun
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.annotation.Resource
import kotlin.test.assertFailsWith

class StubFunctionOutTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.HasFunctionToStub"
        private const val STUB_ME_OUT_ANNOTATION = "net.corda.gradle.jarfilter.StubMeOut"
        private const val PARAMETER_ANNOTATION = "net.corda.gradle.Parameter"

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "stub-function").build()
        }
    }

    @Test
    fun stubFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val stubMeOut = cl.load<Annotation>(STUB_ME_OUT_ANNOTATION)
            val parameter = cl.load<Annotation>(PARAMETER_ANNOTATION)

            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also { obj ->
                    assertEquals(MESSAGE, obj.unwantedFun(MESSAGE))
                }
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertTrue(method.isAnnotationPresent (stubMeOut), "StubMeOut annotation missing")
                    assertTrue(method.isAnnotationPresent(Resource::class.java), "Resource annotation missing")
                    method.parameterAnnotations.also { paramAnns ->
                        assertEquals(1, paramAnns.size)
                        assertThat(paramAnns[0])
                            .hasOnlyOneElementSatisfying { a -> a.javaClass.isInstance(parameter) }
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val stubMeOut = cl.load<Annotation>(STUB_ME_OUT_ANNOTATION)
            val parameter = cl.load<Annotation>(PARAMETER_ANNOTATION)

            cl.load<HasUnwantedFun>(FUNCTION_CLASS).apply {
                newInstance().also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedFun(MESSAGE) }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertFalse(method.isAnnotationPresent(stubMeOut), "StubMeOut annotation present")
                    assertTrue(method.isAnnotationPresent(Resource::class.java), "Resource annotation missing")
                    method.parameterAnnotations.also { paramAnns ->
                        assertEquals(1, paramAnns.size)
                        assertThat(paramAnns[0])
                            .hasOnlyOneElementSatisfying { a -> a.javaClass.isInstance(parameter) }
                    }
                }
            }
        }
    }
}
