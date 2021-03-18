package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.unwanted.HasUnwantedVar
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteVarPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasUnwantedVarPropertyForDelete"
        private const val GETTER_CLASS = "net.corda.gradle.HasUnwantedGetForDelete"
        private const val SETTER_CLASS = "net.corda.gradle.HasUnwantedSetForDelete"
        private const val JVM_FIELD_CLASS = "net.corda.gradle.HasVarJvmFieldForDelete"

        private val unwantedVar = isProperty("unwantedVar", String::class)
        private val getUnwantedVar = isMethod("getUnwantedVar", String::class.java)
        private val setUnwantedVar = isMethod("setUnwantedVar", Void.TYPE, String::class.java)
        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-var-property").build()
        }
    }

    @Test
    fun deleteProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                assertTrue(getDeclaredField("unwantedVar").hasModifiers(ACC_PRIVATE))
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
                assertThat("getUnwantedVar not found", javaDeclaredMethods, hasItem(getUnwantedVar))
                assertThat("setUnwantedVar not found", javaDeclaredMethods, hasItem(setUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar }
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar = MESSAGE }
                }
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVar") }
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
                assertThat("getUnwantedVar still exists", javaDeclaredMethods, not(hasItem(getUnwantedVar)))
                assertThat("setUnwantedVar still exists", javaDeclaredMethods, not(hasItem(setUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                assertTrue(getDeclaredField("unwantedVar").hasModifiers(ACC_PRIVATE))
                assertThat("getUnwantedVar not found", javaDeclaredMethods, hasItem(getUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar }
                }
                assertTrue(getDeclaredField("unwantedVar").hasModifiers(ACC_PRIVATE))
                assertThat("getUnwantedVar still exists", javaDeclaredMethods, not(hasItem(getUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteSetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                getDeclaredField("unwantedVar").also { field ->
                    assertTrue(field.hasModifiers(ACC_PRIVATE))
                }
                assertThat("setUnwantedVar not found", javaDeclaredMethods, hasItem(setUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar = MESSAGE }
                }
                getDeclaredField("unwantedVar").also { field ->
                    assertTrue(field.hasModifiers(ACC_PRIVATE))
                }
                assertThat("setUnwantedVar still exists", javaDeclaredMethods, not(hasItem(setUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteJvmField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                val obj: Any = getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE)
                getDeclaredField("unwantedVar").also { field ->
                    assertEquals(DEFAULT_MESSAGE, field.get(obj))
                    field.set(obj, MESSAGE)
                    assertEquals(MESSAGE, field.get(obj))
                }
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVar") }
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
            }
        }
    }
}