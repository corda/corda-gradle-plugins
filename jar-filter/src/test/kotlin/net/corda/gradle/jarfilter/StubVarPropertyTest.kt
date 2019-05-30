package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedVar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

class StubVarPropertyTest {
    companion object {
        private const val GETTER_CLASS = "net.corda.gradle.HasUnwantedGetForStub"
        private const val SETTER_CLASS = "net.corda.gradle.HasUnwantedSetForStub"
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "stub-var-property").build()
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedVar }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
            }
        }
    }

    @Test
    fun deleteSetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                }
            }
        }
    }
}