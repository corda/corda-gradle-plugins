package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.classMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

/**
 * Sealed classes can have non-nested subclasses, so long as those subclasses
 * are declared in the same file as the sealed class. Check that the metadata
 * is still updated correctly in this case.
 */
@TestInstance(PER_CLASS)
class DeleteSealedSubclassTest {
    private companion object {
        private const val SEALED_CLASS = "net.corda.gradle.SealedBaseClass"
        private const val WANTED_SUBCLASS = "net.corda.gradle.WantedSubclass"
        private const val UNWANTED_SUBCLASS = "net.corda.gradle.UnwantedSubclass"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-sealed-subclass").build()
    }

    @Test
    fun deleteUnwantedSubclass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(UNWANTED_SUBCLASS)
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classMetadata.sealedSubclasses)
                    .containsExactlyInAnyOrder(WANTED_SUBCLASS, UNWANTED_SUBCLASS)
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(WANTED_SUBCLASS)
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_SUBCLASS) }
            cl.load<Any>(SEALED_CLASS).apply {
                assertTrue(kotlin.isSealed)
                assertThat(classMetadata.sealedSubclasses)
                    .containsExactly(WANTED_SUBCLASS)
            }
        }
    }
}