package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import org.assertj.core.api.Assertions.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

class DeleteStaticFunctionTest {
    companion object {
        private const val FUNCTION_CLASS = "net.corda.gradle.StaticFunctionsToDelete"

        private val unwantedInline = isMethod("unwantedInlineToDelete", Any::class.java, String::class.java, Class::class.java)
        private val defaultUnwantedInline = isMethod("unwantedInlineToDelete\$default", Any::class.java, String::class.java, Class::class.java, Integer.TYPE, Any::class.java)
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-static-function").build()
        }
    }

    @Test
    fun deleteStringFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedStringToDelete", String::class.java).also { method ->
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
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedStringToDelete", String::class.java) }
            }
        }
    }

    @Test
    fun deleteLongFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedLongToDelete", Long::class.java).also { method ->
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
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedLongToDelete", Long::class.java) }
            }
        }
    }

    @Test
    fun deleteIntFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(FUNCTION_CLASS).apply {
                getMethod("unwantedIntToDelete", Int::class.java).also { method ->
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
                assertFailsWith<NoSuchMethodException> { getMethod("unwantedIntToDelete", Int::class.java) }
            }
        }
    }

    @Test
    fun deleteInlineFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<Any>(FUNCTION_CLASS)) {
                assertThat("unwantedInlineToDelete(String, Class) missing", declaredMethods.toList(), hasItem(unwantedInline))
                assertThat("unwantedInlineToDelete\$default(String, Class) missing", declaredMethods.toList(), hasItem(defaultUnwantedInline))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<Any>(FUNCTION_CLASS)) {
                assertThat("unwantedInlineToDelete(String, Class) still exists", declaredMethods.toList(), not(hasItem(unwantedInline)))
                assertThat("unwantedInlineToDelete\$default(String, Class) still exists", declaredMethods.toList(), not(hasItem(defaultUnwantedInline)))
            }
        }
    }
}
