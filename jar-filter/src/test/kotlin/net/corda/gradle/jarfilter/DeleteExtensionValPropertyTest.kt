package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isExtensionProperty
import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.jarfilter.matcher.typeOfList
import net.corda.gradle.unwanted.HasUnwantedVal
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberProperties

class DeleteExtensionValPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValExtension"

        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val listUnwantedVal = isExtensionProperty("unwantedVal", String::class, typeOfList(String::class))
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java, List::class.java)
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-extension-val").build()
        }
    }

    @Test
    fun deleteExtensionProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
                assertThat("List.unwantedVal not found", kotlin.declaredMemberExtensionProperties, hasItem(listUnwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
                assertThat("List.unwantedVal still exists", kotlin.declaredMemberExtensionProperties, not(hasItem(listUnwantedVal)))
            }
        }
    }
}
