package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isConstructor
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE.JAVA_17
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@EnabledForJreRange(min = JAVA_17)
class DeleteJavaRecordConstructorTest {
    companion object {
        private const val RECORD_CLASS = "net.corda.gradle.RecordWithConstructor"

        private val noArgs = isConstructor(RECORD_CLASS)

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-java-record-constructor").build()
        }
    }

    @Test
    fun deleteRecordConstructor() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(RECORD_CLASS).apply {
                assertThat("constructor() missing", constructors.toList(), hasItem(noArgs))
                getDeclaredConstructor().newInstance().also { obj ->
                    assertEquals(DEFAULT_MESSAGE, getMethod("name").invoke(obj))
                    assertEquals(0, getMethod("number").invoke(obj))
                    assertEquals(-1L, getMethod("bigNumber").invoke(obj))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(RECORD_CLASS).apply {
                assertThat("constructor() found", constructors.toList(), not(hasItem(noArgs)))
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
                getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .newInstance(MESSAGE, NUMBER, BIG_NUMBER).also { obj ->
                        assertEquals(MESSAGE, getMethod("name").invoke(obj))
                        assertEquals(NUMBER, getMethod("number").invoke(obj))
                        assertEquals(BIG_NUMBER, getMethod("bigNumber").invoke(obj))
                    }
            }
        }
    }
}
