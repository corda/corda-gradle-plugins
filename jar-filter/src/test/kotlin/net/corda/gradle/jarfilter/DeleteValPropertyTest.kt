package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.unwanted.HasUnwantedVal
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

@TestInstance(PER_CLASS)
class DeleteValPropertyTest {
    private companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValPropertyForDelete"
        private const val GETTER_CLASS = "net.corda.gradle.HasValGetterForDelete"
        private const val JVM_FIELD_CLASS = "net.corda.gradle.HasValJvmFieldForDelete"

        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java)
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-val-property").build()
    }

    @Test
    fun deleteProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertTrue(getDeclaredField("unwantedVal").hasModifiers(ACC_PRIVATE))
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal not found", javaDeclaredMethods, hasItem(getUnwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVal }
                }
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVal") }
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
                assertThat("getUnwantedVal still exists", javaDeclaredMethods, not(hasItem(getUnwantedVal)))
            }
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertTrue(getDeclaredField("unwantedVal").hasModifiers(ACC_PRIVATE))
                assertThat("getUnwantedVal not found", javaDeclaredMethods, hasItem(getUnwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVal }
                }
                assertTrue(getDeclaredField("unwantedVal").hasModifiers(ACC_PRIVATE))
                assertThat("getUnwantedVal still exists", javaDeclaredMethods, not(hasItem(getUnwantedVal)))
            }
        }
    }

    @Test
    fun deleteJvmField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                val obj = getDeclaredConstructor(String::class.java).newInstance(MESSAGE)
                getDeclaredField("unwantedVal").also { field ->
                    assertEquals(MESSAGE, field.get(obj))
                }
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVal") }
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
            }
        }
    }
}
