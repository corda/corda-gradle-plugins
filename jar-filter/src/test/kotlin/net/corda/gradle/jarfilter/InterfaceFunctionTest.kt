package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Modifier.ABSTRACT
import java.nio.file.Path
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class InterfaceFunctionTest {
    private companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.InterfaceFunctions"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "interface-function").build()
    }

    @Test
    fun deleteInterfaceFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("toDelete", Long::class.java).also { method ->
                    assertEquals(ABSTRACT, method.modifiers and ABSTRACT)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod("toDelete", Long::class.java) }
            }
        }
    }

    @Test
    fun cannotStubInterfaceFunction() {
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
