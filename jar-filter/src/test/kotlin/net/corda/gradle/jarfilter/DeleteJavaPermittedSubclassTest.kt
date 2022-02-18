package net.corda.gradle.jarfilter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE.JAVA_17
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFailsWith

@EnabledForJreRange(min = JAVA_17)
@TestInstance(PER_CLASS)
class DeleteJavaPermittedSubclassTest {
    private companion object {
        private const val SEALED_CLASS = "net.corda.gradle.WantedSealedClass"
        private const val WANTED_SUBCLASS = "net.corda.gradle.WantedSubclass"
        private const val UNWANTED_SUBCLASS = "net.corda.gradle.UnwantedSubclass"
    }

    private lateinit var testProject: JarFilterProject

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-java-permitted-subclass").build()
    }

    @Test
    fun deletePermittedSubclass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(SEALED_CLASS)
            cl.load<Any>(WANTED_SUBCLASS)
            cl.load<Any>(UNWANTED_SUBCLASS)
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(SEALED_CLASS)
            cl.load<Any>(WANTED_SUBCLASS)
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(UNWANTED_SUBCLASS) }
        }
    }
}
