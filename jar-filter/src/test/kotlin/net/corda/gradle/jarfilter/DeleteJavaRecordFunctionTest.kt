package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE.JAVA_17
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@EnabledForJreRange(min = JAVA_17)
class DeleteJavaRecordFunctionTest {
    companion object {
        private const val RECORD_CLASS = "net.corda.gradle.RecordWithFunction"

        private val getMessage = isMethod("getMessage", String::class.java)

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-java-record-function").build()
        }
    }

    @Test
    fun deleteRecordFunction() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(RECORD_CLASS).apply {
                getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType).newInstance(MESSAGE, NUMBER, BIG_NUMBER)
                assertThat("getMessage() missing", javaDeclaredMethods, hasItem(getMessage))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(RECORD_CLASS).apply {
                getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType).newInstance(MESSAGE, NUMBER, BIG_NUMBER)
                assertThat("getMessage() found", javaDeclaredMethods, not(hasItem(getMessage)))
            }
        }
    }
}
