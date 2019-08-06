package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.jvm.kotlin
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class SanitiseDeleteConstructorTest {
    companion object {
        private const val COMPLEX_CONSTRUCTOR_CLASS = "net.corda.gradle.HasOverloadedComplexConstructorToDelete"

        private val isDefaultConstructorMarker = equalTo("kotlin.jvm.internal.DefaultConstructorMarker")
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "sanitise-delete-constructor").build()
        }
    }

    @Test
    fun deleteOverloadedLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasOverloadedLongConstructorToDelete",
        listOf(
            arrayOf(),
            arrayOf(matches(java.lang.Long.TYPE), matches(Integer.TYPE), isDefaultConstructorMarker)
        )
    )

    @Test
    fun deleteMultipleLongConstructor() = checkClassWithLongParameter(
        "net.corda.gradle.HasMultipleLongConstructorsToDelete",
        listOf(arrayOf())
    )

    private fun checkClassWithLongParameter(longClass: String, deleted: List<Array<Matcher<in String>>>) {
        val longConstructor = isConstructor(longClass, Long::class)
        val deletedConstructors = deleted.map { args -> isConstructor(equalTo(longClass), *args) }.toTypedArray()

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasLong>(longClass)) {
                assertThat("Java constructors not found", constructors.toList(), hasItems(*deletedConstructors))

                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                assertThat("<init>(J) not found", kotlin.constructors, hasItem(longConstructor))
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).longData()).isEqualTo(0)
                assertThat(newInstance().longData()).isEqualTo(0)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasLong>(longClass)) {
                deletedConstructors.forEach { deleted ->
                    assertThat("Java constructor still exists", constructors.toList(), not(hasItem(deleted)))
                }

                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also {
                    assertEquals(BIG_NUMBER, it.longData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(J) not found", this, hasItem(longConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(BIG_NUMBER).longData()).isEqualTo(BIG_NUMBER)

                assertNull(kotlin.noArgConstructor, "no-arg constructor exists")
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }

    @Test
    fun deleteOverloadedIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasOverloadedIntConstructorToDelete",
        listOf(
            arrayOf(),
            arrayOf(matches(Integer.TYPE), matches(Integer.TYPE), isDefaultConstructorMarker)
        )
    )

    @Test
    fun deleteMultipleIntConstructor() = checkClassWithIntParameter(
        "net.corda.gradle.HasMultipleIntConstructorsToDelete",
        listOf(arrayOf())
    )

    private fun checkClassWithIntParameter(intClass: String, deleted: List<Array<Matcher<in String>>>) {
        val intConstructor = isConstructor(intClass, Int::class)
        val deletedConstructors = deleted.map { args -> isConstructor(equalTo(intClass), *args) }.toTypedArray()

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasInt>(intClass)) {
                assertThat("Java constructors not found", constructors.toList(), hasItems(*deletedConstructors))

                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                assertThat("<init>(I) not found", kotlin.constructors, hasItem(intConstructor))
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).intData()).isEqualTo(0)
                assertThat(newInstance().intData()).isEqualTo(0)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasInt>(intClass)) {
                deletedConstructors.forEach { deleted ->
                    assertThat("Java constructor still exists", constructors.toList(), not(hasItem(deleted)))
                }

                getDeclaredConstructor(Int::class.java).newInstance(NUMBER).also {
                    assertEquals(NUMBER, it.intData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(I) not found", this, hasItem(intConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(NUMBER).intData()).isEqualTo(NUMBER)

                assertNull(kotlin.noArgConstructor, "no-arg constructor exists")
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }

    @Test
    fun deleteOverloadedStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasOverloadedStringConstructorToDelete",
        listOf(
            arrayOf(),
            arrayOf(matches(String::class.java), matches(Integer.TYPE), isDefaultConstructorMarker)
        )
    )

    @Test
    fun deleteMultipleStringConstructor() = checkClassWithStringParameter(
        "net.corda.gradle.HasMultipleStringConstructorsToDelete",
        listOf(arrayOf())
    )

    private fun checkClassWithStringParameter(stringClass: String, deleted: List<Array<Matcher<in String>>>) {
        val stringConstructor = isConstructor(stringClass, String::class)
        val deletedConstructors = deleted.map { args -> isConstructor(equalTo(stringClass), *args) }.toTypedArray()

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<HasString>(stringClass)) {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                assertThat("<init>(String) not found", kotlin.constructors, hasItem(stringConstructor))
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                val noArg = kotlin.noArgConstructor ?: fail("no-arg constructor missing")
                assertThat(noArg.callBy(emptyMap()).stringData()).isEqualTo(DEFAULT_MESSAGE)
                assertThat(newInstance().stringData()).isEqualTo(DEFAULT_MESSAGE)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            with(cl.load<HasString>(stringClass)) {
                deletedConstructors.forEach { deleted ->
                    assertThat("Java constructor still exists", constructors.toList(), not(hasItem(deleted)))
                }

                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also {
                    assertEquals(MESSAGE, it.stringData())
                }
                kotlin.constructors.apply {
                    assertThat("<init>(String) not found", this, hasItem(stringConstructor))
                    assertEquals(1, this.size)
                }
                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                assertThat(primary.call(MESSAGE).stringData()).isEqualTo(MESSAGE)

                assertNull(kotlin.noArgConstructor, "no-arg constructor exists")
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor() }
            }
        }
    }

    @Test
    fun deleteOverloadedComplexConstructor() {
        val complexConstructor = isConstructor(COMPLEX_CONSTRUCTOR_CLASS, Int::class, String::class)
        val syntheticConstructor = isConstructor(
            equalTo(COMPLEX_CONSTRUCTOR_CLASS),
            matches(Integer.TYPE), matches(String::class.java), matches(Integer.TYPE), isDefaultConstructorMarker
        )

        classLoaderFor(testProject.sourceJar).use { cl ->
            with(cl.load<Any>(COMPLEX_CONSTRUCTOR_CLASS)) {
                assertThat("synthetic <init>(int,String) not found", constructors.toList(), hasItem(syntheticConstructor))

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
                assertThat("synthetic <init>(int,String) still exists", constructors.toList(), not(hasItem(syntheticConstructor)))

                kotlin.constructors.apply {
                    assertThat("<init>(Int,String) not found", this, hasItem(complexConstructor))
                    assertEquals(1, this.size)
                }

                val primary = kotlin.primaryConstructor ?: fail("primary constructor missing")
                primary.call(NUMBER, MESSAGE).also { complex ->
                    assertThat((complex as HasString).stringData()).isEqualTo(MESSAGE)
                    assertThat((complex as HasInt).intData()).isEqualTo(NUMBER)
                }

                assertThat(assertFailsWith<IllegalArgumentException> { primary.callBy(mapOf(primary.parameters[1] to MESSAGE)) })
                    .hasMessageContaining("No argument provided for a required parameter")
                assertFailsWith<NoSuchMethodException> { getDeclaredConstructor(String::class.java) }
            }
        }
    }
}