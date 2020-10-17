package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.classMetadata
import net.corda.gradle.jarfilter.matcher.isKClass
import org.assertj.core.api.Assertions.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

class DeleteNestedClassTest {
    companion object {
        private const val HOST_CLASS = "net.corda.gradle.HasNestedClasses"
        private const val KEPT_CLASS = "$HOST_CLASS\$OneToKeep"
        private const val DELETED_CLASS = "$HOST_CLASS\$OneToThrowAway"

        private const val SEALED_CLASS = "net.corda.gradle.SealedClass"
        private const val WANTED_SUBCLASS = "$SEALED_CLASS\$Wanted"
        private const val UNWANTED_SUBCLASS = "$SEALED_CLASS\$Unwanted"

        private const val OUTER_CLASS = "net.corda.gradle.OuterClass"
        private const val INNER_CLASS = "$OUTER_CLASS\$InnerClass"
        private const val STATIC_INNER_CLASS = "$OUTER_CLASS\$StaticInnerClass"
        private const val LEFT_OVER_CLASS = "$OUTER_CLASS\$LeftOver"
        private const val DELETED_OUTER_CLASS = "net.corda.gradle.DeletedOuterClass"
        private const val DELETED_INNER_CLASS = "$DELETED_OUTER_CLASS\$InnerClass"

        private val keptClass = isKClass(KEPT_CLASS)
        private val deletedClass = isKClass(DELETED_CLASS)
        private val wantedSubclass = isKClass(WANTED_SUBCLASS)
        private val unwantedSubclass = isKClass(UNWANTED_SUBCLASS)

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "delete-nested-class").build()
        }
    }

    @Test
    fun deleteNestedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val deleted = cl.load<Any>(DELETED_CLASS)
            val kept = cl.load<Any>(KEPT_CLASS)
            cl.load<Any>(HOST_CLASS).apply {
                assertThat(declaredClasses).containsExactlyInAnyOrder(deleted, kept)
                assertThat("OneToThrowAway class is missing", kotlin.nestedClasses, hasItem(deletedClass))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(keptClass))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_CLASS) }
            val kept = cl.load<Any>(KEPT_CLASS)
            cl.load<Any>(HOST_CLASS).apply {
                assertThat(declaredClasses).containsExactly(kept)
                assertThat("OneToThrowAway class still exists", kotlin.nestedClasses, not(hasItem(deletedClass)))
                assertThat("OneToKeep class is missing", kotlin.nestedClasses, hasItem(keptClass))
            }
        }
    }

    @Test
    fun deleteFromSealedClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val unwanted = cl.load<Any>(UNWANTED_SUBCLASS)
            val wanted = cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(declaredClasses).containsExactlyInAnyOrder(wanted, unwanted)
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(wantedSubclass))
                assertThat("Unwanted class is missing", kotlin.nestedClasses, hasItem(unwantedSubclass))
                assertThat(classMetadata.sealedSubclasses).containsExactlyInAnyOrder(WANTED_SUBCLASS, UNWANTED_SUBCLASS)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_SUBCLASS) }
            val wanted = cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(declaredClasses).containsExactly(wanted)
                assertThat("Unwanted class still exists", kotlin.nestedClasses, not(hasItem(unwantedSubclass)))
                assertThat("Wanted class is missing", kotlin.nestedClasses, hasItem(wantedSubclass))
                assertThat(classMetadata.sealedSubclasses).containsExactly(WANTED_SUBCLASS)
            }
        }
    }

    @Test
    fun deleteJavaInnerClasses() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val outerClass = cl.load<Any>(OUTER_CLASS)
            val innerClass = cl.load<Any>(INNER_CLASS)
            val staticInnerClass = cl.load<Any>(STATIC_INNER_CLASS)
            val leftOverClass = cl.load<Any>(LEFT_OVER_CLASS)
            assertThat(outerClass.classes).containsExactlyInAnyOrder(innerClass, staticInnerClass, leftOverClass)
            assertThat(innerClass.enclosingClass).isEqualTo(outerClass)
            assertThat(staticInnerClass.enclosingClass).isEqualTo(outerClass)
            assertThat(leftOverClass.enclosingClass).isEqualTo(outerClass)
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val outerClass = cl.load<Any>(OUTER_CLASS)
            val leftOverClass = cl.load<Any>(LEFT_OVER_CLASS)
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(INNER_CLASS) }
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(STATIC_INNER_CLASS) }
            assertThat(outerClass.declaredClasses).containsExactly(leftOverClass)
            assertThat(leftOverClass.enclosingClass).isEqualTo(outerClass)
        }
    }

    @Test
    fun deleteJavaOuterClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val outerClass = cl.load<Any>(DELETED_OUTER_CLASS)
            val innerClass = cl.load<Any>(DELETED_INNER_CLASS)
            assertThat(outerClass.declaredClasses).containsExactly(innerClass)
            assertThat(innerClass.enclosingClass).isEqualTo(outerClass)
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_OUTER_CLASS) }
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_INNER_CLASS) }
        }
    }
}
