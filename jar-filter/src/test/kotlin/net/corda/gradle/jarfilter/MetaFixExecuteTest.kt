package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isFunction
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.unwanted.HasAll
import net.corda.gradle.unwanted.HasAllVal
import net.corda.gradle.unwanted.HasAllVar
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties

class MetaFixExecuteTest {
    companion object {
        private const val EXAMPLE_CLASS = "net.corda.gradle.ExampleKotlin"
        private const val NESTED_CLASS = "net.corda.gradle.ExampleKotlin\$Nested"
        private const val INNER_CLASS = "net.corda.gradle.ExampleKotlin\$Inner"

        private val stringData = isFunction("stringData", String::class)
        private val longData = isFunction("longData", Long::class)
        private val intData = isFunction("intData", Int::class)
        private val stringVal = isProperty("stringVal", String::class)
        private val longVal = isProperty("longVal", Long::class)
        private val intVal = isProperty("intVal", Int::class)
        private val stringVar = isProperty("stringVar", String::class)
        private val longVar = isProperty("longVar", Long::class)
        private val intVar = isProperty("intVar", Int::class)

        private lateinit var testProject: MetaFixProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = MetaFixProject(testProjectDir, "basic-execute").build()
        }
    }

    @Test
    fun testTaskOutput() {
        assertThat(testProject.output)
            .containsOnlyOnce("RESULT: ${testProject.metafixedJar.fileName}")
    }

    @Test
    fun testExampleClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasAll>(EXAMPLE_CLASS).apply {
                assertThat("stringData() not found", kotlin.declaredFunctions, hasItem(stringData))
                assertThat("longData() not found", kotlin.declaredFunctions, hasItem(longData))
                assertThat("intData() not found", kotlin.declaredFunctions, hasItem(intData))
            }
        }

        classLoaderFor(testProject.metafixedJar).use { cl ->
            cl.load<HasAll>(EXAMPLE_CLASS).apply {
                assertThat("stringData() not found", kotlin.declaredFunctions, hasItem(stringData))
                assertThat("longData() not found", kotlin.declaredFunctions, hasItem(longData))
                assertThat("intData() not found", kotlin.declaredFunctions, hasItem(intData))
            }
        }
    }

    @Test
    fun testNestedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasAllVal>(NESTED_CLASS).apply {
                assertThat("stringVal not found", kotlin.declaredMemberProperties, hasItem(stringVal))
                assertThat("longVal not found", kotlin.declaredMemberProperties, hasItem(longVal))
                assertThat("intVal not found", kotlin.declaredMemberProperties, hasItem(intVal))
            }
        }

        classLoaderFor(testProject.metafixedJar).use { cl ->
            cl.load<HasAllVal>(NESTED_CLASS).apply {
                assertThat("stringVal not found", kotlin.declaredMemberProperties, hasItem(stringVal))
                assertThat("longVal not found", kotlin.declaredMemberProperties, hasItem(longVal))
                assertThat("intVal not found", kotlin.declaredMemberProperties, hasItem(intVal))
            }
        }
    }

    @Test
    fun testInnerClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasAllVar>(INNER_CLASS).apply {
                assertThat("stringVar not found", kotlin.declaredMemberProperties, hasItem(stringVar))
                assertThat("longVar not found", kotlin.declaredMemberProperties, hasItem(longVar))
                assertThat("intVar not found", kotlin.declaredMemberProperties, hasItem(intVar))
            }
        }

        classLoaderFor(testProject.metafixedJar).use { cl ->
            cl.load<HasAllVar>(INNER_CLASS).apply {
                assertThat("stringVar not found", kotlin.declaredMemberProperties, hasItem(stringVar))
                assertThat("longVar not found", kotlin.declaredMemberProperties, hasItem(longVar))
                assertThat("intVar not found", kotlin.declaredMemberProperties, hasItem(intVar))
            }
        }
    }
}