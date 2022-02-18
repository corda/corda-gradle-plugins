package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteMultiFileTest {
    private companion object {
        private const val MULTIFILE_CLASS = "net.corda.gradle.HasMultiData"
        private const val STRING_METHOD = "stringToDelete"
        private const val LONG_METHOD = "longToDelete"
        private const val INT_METHOD = "intToDelete"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-multifile").build()
    }

    @Test
    fun deleteStringFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(STRING_METHOD, String::class.java).also { method ->
                    method.invoke(null, MESSAGE).also { result ->
                        assertThat(result)
                            .isInstanceOf(String::class.java)
                            .isEqualTo(MESSAGE)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(STRING_METHOD, String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(LONG_METHOD, Long::class.java).also { method ->
                    method.invoke(null, BIG_NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Long::class.javaObjectType)
                            .isEqualTo(BIG_NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(LONG_METHOD, Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                getMethod(INT_METHOD, Int::class.java).also { method ->
                    method.invoke(null, NUMBER).also { result ->
                        assertThat(result)
                            .isInstanceOf(Int::class.javaObjectType)
                            .isEqualTo(NUMBER)
                    }
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(MULTIFILE_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getMethod(INT_METHOD, Int::class.java) }
            }
        }
    }
}