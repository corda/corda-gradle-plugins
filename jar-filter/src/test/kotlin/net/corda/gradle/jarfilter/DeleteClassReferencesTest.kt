package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasStringVal
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteClassReferencesTest {
    companion object {
        private const val USES_UNWANTED_CLASS = "net.corda.gradle.UsesUnwantedData"
        private const val UNWANTED_CLASS = "net.corda.gradle.UnwantedData"

        private val inputMethod = isMethod(equalTo("input"), isVoid, equalTo(UNWANTED_CLASS))
        private val inputFunction = isFunction(equalTo("input"), isUnit, hasParam(equalTo(UNWANTED_CLASS)))
        private val outputMethod = isMethod(equalTo("output"), equalTo(UNWANTED_CLASS))
        private val outputFunction = isFunction(equalTo("output"), equalTo(UNWANTED_CLASS))
        private val unwantedVar = isProperty(equalTo("unwantedVar"), equalTo(UNWANTED_CLASS))
        private val unwantedVal = isProperty(equalTo("unwantedVal"), equalTo(UNWANTED_CLASS))
        private val jvmUnwantedVar = isProperty(equalTo("jvmUnwantedVar"), equalTo(UNWANTED_CLASS))
        private val jvmUnwantedVal = isProperty(equalTo("jvmUnwantedVal"), equalTo(UNWANTED_CLASS))
        private val jvmCompanionVar = isField(equalTo("jvmCompanionVar"), equalTo(UNWANTED_CLASS))
        private val jvmCompanionVal = isField(equalTo("jvmCompanionVal"), equalTo(UNWANTED_CLASS))

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-class-references").build()
        }
    }

    @Test
    fun deleteClassReferences() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasStringVal>(UNWANTED_CLASS)
            cl.load<Any>(USES_UNWANTED_CLASS).apply {
                assertThat("input() not found", javaDeclaredMethods, hasItem(inputMethod))
                assertThat("output() not found", javaDeclaredMethods, hasItem(outputMethod))
                assertThat("input() not found", kotlin.declaredFunctions, hasItem(inputFunction))
                assertThat("output() not found", kotlin.declaredFunctions, hasItem(outputFunction))
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("jvmUnwantedVar not found", kotlin.declaredMemberProperties, hasItem(jvmUnwantedVar))
                assertThat("jvmUnwantedVal not found", kotlin.declaredMemberProperties, hasItem(jvmUnwantedVal))
                assertThat("jvmCompanionVar not found", javaDeclaredFields, hasItem(jvmCompanionVar))
                assertThat("jvmUnwantedVal not found", javaDeclaredFields, hasItem(jvmCompanionVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> {
                cl.load<HasStringVal>(UNWANTED_CLASS)
            }
            cl.load<Any>(USES_UNWANTED_CLASS).apply {
                assertThat("input() still exists", javaDeclaredMethods, not(hasItem(inputMethod)))
                assertThat("output() still exists", javaDeclaredMethods, not(hasItem(outputMethod)))
                assertThat("input() still exists", kotlin.declaredFunctions, not(hasItem(inputFunction)))
                assertThat("output() still exists", kotlin.declaredFunctions, not(hasItem(outputFunction)))
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
                assertThat("jvmUnwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(jvmUnwantedVar)))
                assertThat("jvmUnwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(jvmUnwantedVal)))
                assertThat("jvmCompanionVar still exists", javaDeclaredFields, not(hasItem(jvmCompanionVar)))
                assertThat("jvmCompanionVal still exists", javaDeclaredFields, not(hasItem(jvmCompanionVal)))
            }
        }
    }
}
