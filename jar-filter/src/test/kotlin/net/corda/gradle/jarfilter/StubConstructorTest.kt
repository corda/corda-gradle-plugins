package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isConstructor
import net.corda.gradle.unwanted.HasAll
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class StubConstructorTest {
    companion object {
        private const val STRING_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryStringConstructorToStub"
        private const val LONG_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryLongConstructorToStub"
        private const val INT_PRIMARY_CONSTRUCTOR_CLASS = "net.corda.gradle.PrimaryIntConstructorToStub"
        private const val SECONDARY_CONSTRUCTOR_CLASS = "net.corda.gradle.HasConstructorToStub"
        private const val DEFAULT_VALUE_PRIMARY_CLASS = "net.corda.gradle.PrimaryConstructorWithDefaultToStub"
        private const val DEFAULT_VALUE_SECONDARY_CLASS = "net.corda.gradle.SecondaryConstructorWithDefaultToStub"

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "stub-constructor").build()
        }
    }

    @Test
    fun stubConstructorWithLongParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also { obj ->
                    assertEquals(BIG_NUMBER, obj.longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(BIG_NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubConstructorWithStringParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(MESSAGE) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun showUnannotatedConstructorIsUnaffected() {
        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasAll>(SECONDARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also { obj ->
                    assertEquals(NUMBER, obj.intData())
                    assertEquals(NUMBER.toLong(), obj.longData())
                    assertEquals("<nothing>", obj.stringData())
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithStringParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(STRING_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(String::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(MESSAGE) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithLongParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also { obj ->
                    assertEquals(BIG_NUMBER, obj.longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLong>(LONG_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Long::class.java).also {
                    assertFailsWith<InvocationTargetException> { it.newInstance(BIG_NUMBER) }.targetException.also { ex ->
                        assertThat(ex)
                            .isInstanceOf(UnsupportedOperationException::class.java)
                            .hasMessage("Method has been deleted")
                    }
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithIntParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also { obj ->
                    assertEquals(NUMBER, obj.intData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasInt>(INT_PRIMARY_CONSTRUCTOR_CLASS).apply {
                getDeclaredConstructor(Int::class.java).apply {
                    val error = assertFailsWith<InvocationTargetException> { newInstance(NUMBER) }.targetException
                    assertThat(error)
                        .isInstanceOf(UnsupportedOperationException::class.java)
                        .hasMessage("Method has been deleted")
                }
            }
        }
    }

    @Test
    fun stubPrimaryConstructorWithDefaultParameter() {
        val defaultValueConstructor = isConstructor(DEFAULT_VALUE_PRIMARY_CLASS, String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasString>(DEFAULT_VALUE_PRIMARY_CLASS)) {
                val primaryConstructor = kotlin.primaryConstructor ?: fail("Must have a primary constructor")
                assertThat("Incorrect primary constructor", primaryConstructor, defaultValueConstructor)

                with(primaryConstructor.callBy(emptyMap())) {
                    assertEquals(DEFAULT_MESSAGE, stringData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasString>(DEFAULT_VALUE_PRIMARY_CLASS)) {
                val primaryConstructor = kotlin.primaryConstructor ?: fail("Must have a primary constructor")
                assertThat("Incorrect primary constructor", primaryConstructor, defaultValueConstructor)

                val error = assertFailsWith<InvocationTargetException> {
                    primaryConstructor.callBy(emptyMap())
                }
                assertThat(error.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
            }
        }
    }

    @Test
    fun stubSecondaryConstructorWithDefaultParameter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasAll>(DEFAULT_VALUE_SECONDARY_CLASS)) {
                val primaryConstructor = kotlin.primaryConstructor ?: fail("Must have a primary constructor")
                with(primaryConstructor.call(MESSAGE, BIG_NUMBER)) {
                    assertEquals(MESSAGE, stringData())
                    assertEquals(BIG_NUMBER, longData())
                }

                val secondaryConstructor = (kotlin.constructors - primaryConstructor).single()
                with(secondaryConstructor.callBy(emptyMap())) {
                    assertEquals(DEFAULT_MESSAGE, stringData())
                    assertEquals(0L, longData())
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasAll>(DEFAULT_VALUE_SECONDARY_CLASS)) {
                val primaryConstructor = kotlin.primaryConstructor ?: fail("Must have a primary constructor")
                with(primaryConstructor.call(MESSAGE, BIG_NUMBER)) {
                    assertEquals(MESSAGE, stringData())
                    assertEquals(BIG_NUMBER, longData())
                }

                val secondaryConstructor = (kotlin.constructors - primaryConstructor).single()
                val kotlinError = assertFailsWith<InvocationTargetException> {
                    secondaryConstructor.callBy(emptyMap())
                }
                assertThat(kotlinError.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")

                val javaSecondaryConstructor = getDeclaredConstructor(String::class.java)
                val javaError = assertFailsWith<InvocationTargetException> {
                    javaSecondaryConstructor.newInstance(MESSAGE)
                }
                assertThat(javaError.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
            }
        }
    }
}
