package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isConstructor
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.jvm.kotlin
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class SanitiseStubConstructorTest {
    companion object {
        private const val COMPLEX_CONSTRUCTOR_CLASS = "net.corda.gradle.HasOverloadedComplexConstructorToStub"
        private const val COUNT_OVERLOADED = 1
        private const val COUNT_MULTIPLE = 2
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "sanitise-stub-constructor").build()
        }

        fun <T> newInstance(clazz: Class<T>): T {
            return try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    @Test
    fun stubOverloadedLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasOverloadedLongConstructorToStub",
        COUNT_OVERLOADED
    )

    @Test
    fun stubMultipleLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasMultipleLongConstructorsToStub",
        COUNT_MULTIPLE
    )

    private fun checkClassWithLongParameter(longClass: String, constructorCount: Int) {
        val longConstructor = isConstructor(longClass, Long::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasLong>(longClass)) {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(J) not found", this, hasItem(longConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).longData()).isEqualTo(0)
                assertThat(newInstance(this).longData()).isEqualTo(0)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasLong>(longClass)) {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(J) not found", this, hasItem(longConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(assertFailsWith<InvocationTargetException> { noArg.callBy(emptyMap()) }.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
                assertThat(assertFailsWith<UnsupportedOperationException> { newInstance(this) })
                    .hasMessage("Method has been deleted")
            }
        }
    }

    @Test
    fun stubOverloadedIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasOverloadedIntConstructorToStub",
        COUNT_OVERLOADED
    )

    @Test
    fun stubMultipleIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasMultipleIntConstructorsToStub",
        COUNT_MULTIPLE
    )

    private fun checkClassWithIntParameter(intClass: String, constructorCount: Int) {
        val intConstructor = isConstructor(intClass, Int::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasInt>(intClass)) {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(I) not found", this, hasItem(intConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).intData()).isEqualTo(0)
                assertThat(newInstance(this).intData()).isEqualTo(0)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasInt>(intClass)) {
                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(I) not found", this, hasItem(intConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(assertFailsWith<InvocationTargetException> { noArg.callBy(emptyMap()) }.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
                assertThat(assertFailsWith<UnsupportedOperationException> { newInstance(this) })
                    .hasMessage("Method has been deleted")
            }
        }
    }

    @Test
    fun stubOverloadedStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasOverloadedStringConstructorToStub",
        COUNT_OVERLOADED
    )

    @Test
    fun stubMultipleStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasMultipleStringConstructorsToStub",
        COUNT_MULTIPLE
    )

    private fun checkClassWithStringParameter(stringClass: String, constructorCount: Int) {
        val stringConstructor = isConstructor(stringClass, String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasString>(stringClass)) {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(String) not found", this, hasItem(stringConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).stringData()).isEqualTo(DEFAULT_MESSAGE)
                assertThat(newInstance(this).stringData()).isEqualTo(DEFAULT_MESSAGE)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasString>(stringClass)) {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(String) not found", this, hasItem(stringConstructor))
                    assertEquals(constructorCount, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(assertFailsWith<InvocationTargetException> { noArg.callBy(emptyMap()) }.targetException)
                    .isInstanceOf(UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
                assertThat(assertFailsWith<UnsupportedOperationException> { newInstance(this) })
                    .hasMessage("Method has been deleted")
            }
        }
    }

    @Test
    fun stubOverloadedComplexConstructor() {
        val complexConstructor = isConstructor(COMPLEX_CONSTRUCTOR_CLASS, Int::class, String::class)

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<Any>(COMPLEX_CONSTRUCTOR_CLASS)) {
                kotlin.constructors.apply {
                    assertThat("<init>(Int,String) not found", this, hasItem(complexConstructor))
                    assertEquals(1, this.size)
                }

                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                primary.call(NUMBER, MESSAGE).also { complex ->
                    assertThat((complex as HasString).stringData()).isEqualTo(MESSAGE)
                    assertThat((complex as HasInt).intData()).isEqualTo(NUMBER)
                }

                primary.callBy(mapOf(primary.parameters[1] to MESSAGE)).also { complex ->
                    assertThat((complex as HasString).stringData()).isEqualTo(MESSAGE)
                    assertThat((complex as HasInt).intData()).isEqualTo(0)
                }
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { complex ->
                    assertThat((complex as HasString).stringData()).isEqualTo(MESSAGE)
                    assertThat((complex as HasInt).intData()).isEqualTo(0)
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<Any>(COMPLEX_CONSTRUCTOR_CLASS)) {
                kotlin.constructors.apply {
                    assertThat("<init>(Int,String) not found", this, hasItem(complexConstructor))
                    assertEquals(1, this.size)
                }

                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                primary.call(NUMBER, MESSAGE).also { complex ->
                    assertThat((complex as HasString).stringData()).isEqualTo(MESSAGE)
                    assertThat((complex as HasInt).intData()).isEqualTo(NUMBER)
                }

                assertThat(assertFailsWith<InvocationTargetException> { primary.callBy(mapOf(primary.parameters[1] to MESSAGE)) }.targetException)
                    .isInstanceOf(kotlin.UnsupportedOperationException::class.java)
                    .hasMessage("Method has been deleted")
                assertThat(assertFailsWith<InvocationTargetException> { getDeclaredConstructor(String::class.java).newInstance(MESSAGE) }.targetException)
                    .hasMessage("Method has been deleted")
            }

            assertThat(testProject.output).containsSequence(
                "Class net/corda/gradle/HasOverloadedComplexConstructorToStub",
                "- Stubbed out method <init>(ILjava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
                "- Stubbed out method <init>(Ljava/lang/String;)V"
            )
        }
    }

}
