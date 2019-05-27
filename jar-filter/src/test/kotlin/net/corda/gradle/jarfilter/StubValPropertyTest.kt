package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedVal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

class StubValPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValPropertyForStub"
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "stub-val-property").build()
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.unwantedVal }.also { ex ->
                        assertEquals("Method has been deleted", ex.message)
                    }
                }
            }
        }
    }
}
