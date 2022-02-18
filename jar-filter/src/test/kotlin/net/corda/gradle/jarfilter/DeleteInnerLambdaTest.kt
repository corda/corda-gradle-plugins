package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isKonstructor
import net.corda.gradle.unwanted.HasInt
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteInnerLambdaTest {
    private companion object {
        private const val LAMBDA_CLASS = "net.corda.gradle.HasInnerLambda"
        private const val SIZE = 64

        private val constructInt = isKonstructor(LAMBDA_CLASS, Int::class)
        private val constructBytes = isKonstructor(LAMBDA_CLASS, ByteArray::class)
    }

    private lateinit var testProject: JarFilterProject
    private lateinit var sourceClasses: List<String>
    private lateinit var filteredClasses: List<String>

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-inner-lambda").build()
        sourceClasses = testProject.sourceJar.getClassNames(LAMBDA_CLASS)
        filteredClasses = testProject.filteredJar.getClassNames(LAMBDA_CLASS)
    }

    @Test
    fun `test lambda class is deleted`() {
        assertThat(sourceClasses)
            .contains(LAMBDA_CLASS)
            .hasSize(2)
        assertThat(filteredClasses).containsExactly(LAMBDA_CLASS)
    }

    @Test
    fun `test host class`() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(LAMBDA_CLASS).apply {
                getConstructor(Int::class.java).newInstance(SIZE).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
                kotlin.constructors.also { ctors ->
                    assertThat("<init>(Int) not found", ctors, hasItem(constructInt))
                    assertThat("<init>(byte[]) not found", ctors, hasItem(constructBytes))
                }

                getConstructor(ByteArray::class.java).newInstance(ByteArray(SIZE)).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(LAMBDA_CLASS).apply {
                assertFailsWith<NoSuchMethodException> { getConstructor(Int::class.java) }
                kotlin.constructors.also { ctors ->
                    assertThat("<init>(Int) still exists", ctors, not(hasItem(constructInt)))
                    assertThat("<init>(byte[]) not found", ctors, hasItem(constructBytes))
                }

                getConstructor(ByteArray::class.java).newInstance(ByteArray(SIZE)).also { obj ->
                    assertEquals(SIZE, obj.intData())
                }
            }
        }
    }
}