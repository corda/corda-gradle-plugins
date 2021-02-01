package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.unwanted.HasUnwantedVal
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteLazyTest {
    companion object {
        private const val LAZY_VAL_CLASS = "net.corda.gradle.HasLazyVal"

        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java)
        private lateinit var testProject: JarFilterProject
        private lateinit var sourceClasses: List<String>
        private lateinit var filteredClasses: List<String>

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-lazy").build()
            sourceClasses = testProject.sourceJar.getClassNames(LAZY_VAL_CLASS)
            filteredClasses = testProject.filteredJar.getClassNames(LAZY_VAL_CLASS)
        }
    }

    @Test
    fun deletedClasses() {
        assertThat(sourceClasses).contains(LAZY_VAL_CLASS)
        assertThat(filteredClasses).containsExactly(LAZY_VAL_CLASS)
    }

    @Test
    fun deleteLazyVal() {
        assertThat(sourceClasses).anyMatch { it.contains("\$unwantedVal\$") }

        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(LAZY_VAL_CLASS).apply {
                getConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(LAZY_VAL_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getConstructor(String::class.java) }
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
            }
        }
    }
}