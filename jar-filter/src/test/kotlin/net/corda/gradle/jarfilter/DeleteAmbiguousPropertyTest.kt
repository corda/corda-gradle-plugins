package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.isMethod
import net.corda.gradle.jarfilter.matcher.isExtensionProperty
import net.corda.gradle.jarfilter.matcher.javaDeclaredMethods
import net.corda.gradle.jarfilter.matcher.typeOfCollection
import net.corda.gradle.jarfilter.matcher.typeOfMap
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.reflect.full.declaredMemberExtensionProperties

@RequiresKotlin14
class DeleteAmbiguousPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.DeleteAmbiguousValProperty"

        private val nameIntToInt = isMethod("nameIntToInt", String::class.java, Map::class.java)
        private val nameIntToByte = isMethod("nameIntToByte", String::class.java, Map::class.java)
        private val nameString = isMethod("nameString", String::class.java, Collection::class.java)
        private val nameLong = isMethod("nameLong", String::class.java, Collection::class.java)

        private val mapIntToIntName = isExtensionProperty("name", String::class, typeOfMap(Int::class, Int::class))
        private val mapIntToByteName = isExtensionProperty("name", String::class, typeOfMap(Int::class, Byte::class))
        private val collectionStringName = isExtensionProperty("name", String::class, typeOfCollection(String::class))
        private val collectionLongName = isExtensionProperty("name", String::class, typeOfCollection(Long::class))

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-ambiguous-property").build()
        }
    }

    @Test
    fun deleteAmbiguousProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertNotNull(getDeclaredConstructor().newInstance())
                assertThat("nameIntToInt(Map) missing", kotlin.javaDeclaredMethods, hasItem(nameIntToInt))
                assertThat("nameIntToByte(Map) missing", kotlin.javaDeclaredMethods, hasItem(nameIntToByte))
                assertThat("nameString(Collection) missing", kotlin.javaDeclaredMethods, hasItem(nameString))
                assertThat("nameLong(Collection) missing", kotlin.javaDeclaredMethods, hasItem(nameLong))

                assertThat("Map<Int,Int>.name missing", kotlin.declaredMemberExtensionProperties, hasItem(mapIntToIntName))
                assertThat("Map<Int,Byte>.name missing", kotlin.declaredMemberExtensionProperties, hasItem(mapIntToByteName))
                assertThat("Collection<String>.name missing", kotlin.declaredMemberExtensionProperties, hasItem(collectionStringName))
                assertThat("Collection<Long>.name missing", kotlin.declaredMemberExtensionProperties, hasItem(collectionLongName))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(PROPERTY_CLASS).apply {
                assertNotNull(getDeclaredConstructor().newInstance())
                assertThat("nameIntToInt(Map) still exists", kotlin.javaDeclaredMethods, not(hasItem(nameIntToInt)))
                assertThat("nameIntToByte(Map) does not exist", kotlin.javaDeclaredMethods, hasItem(nameIntToByte))
                assertThat("nameString(Collection) does not exist", kotlin.javaDeclaredMethods, hasItem(nameString))
                assertThat("nameLong(Collection) still exists", kotlin.javaDeclaredMethods, not(hasItem(nameLong)))

                assertThat("Map<Int,Int>.name still exists", kotlin.declaredMemberExtensionProperties, not(hasItem(mapIntToIntName)))
                assertThat("Map<Int,Byte>.name does not exist", kotlin.declaredMemberExtensionProperties, hasItem(mapIntToByteName))
                assertThat("Collection<String>.name still exists", kotlin.declaredMemberExtensionProperties, hasItem(collectionStringName))
                assertThat("Collection<Long>.name does not exist", kotlin.declaredMemberExtensionProperties, not(hasItem(collectionLongName)))
            }
        }
    }
}