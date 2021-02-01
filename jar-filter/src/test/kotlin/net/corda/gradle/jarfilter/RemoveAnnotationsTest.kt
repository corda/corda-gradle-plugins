package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.HasUnwantedFun
import net.corda.gradle.unwanted.HasUnwantedVal
import net.corda.gradle.unwanted.HasUnwantedVar
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RemoveAnnotationsTest {
    companion object {
        private const val ANNOTATED_CLASS = "net.corda.gradle.HasUnwantedAnnotations"
        private const val REMOVE_ME_CLASS = "net.corda.gradle.jarfilter.RemoveMe"

        private lateinit var testProject: JarFilterProject

        @BeforeAll
        @JvmStatic
        fun setup(@TempDir testProjectDir: Path) {
            testProject = JarFilterProject(testProjectDir, "remove-annotations").build()
        }
    }

    @Test
    fun deleteFromClass() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                assertNotNull(getAnnotation(removeMe))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                assertNull(getAnnotation(removeMe))
            }
        }
    }

    @Test
    fun deleteFromDefaultConstructor() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getDeclaredConstructor().also { con ->
                    assertNotNull(con.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getDeclaredConstructor().also { con ->
                    assertNull(con.getAnnotation(removeMe))
                }
            }
        }
    }

    @Test
    fun deleteFromPrimaryConstructor() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getDeclaredConstructor(Long::class.java, String::class.java).also { con ->
                    assertNotNull(con.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getDeclaredConstructor(Long::class.java, String::class.java).also { con ->
                    assertNull(con.getAnnotation(removeMe))
                }
            }
        }
    }

    @Test
    fun deleteFromField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getField("longField").also { field ->
                    assertNotNull(field.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<Any>(ANNOTATED_CLASS).apply {
                getField("longField").also { field ->
                    assertNull(field.getAnnotation(removeMe))
                }
            }
        }
    }

    @Test
    fun deleteFromMethod() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedFun>(ANNOTATED_CLASS).apply {
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertNotNull(method.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedFun>(ANNOTATED_CLASS).apply {
                getMethod("unwantedFun", String::class.java).also { method ->
                    assertNull(method.getAnnotation(removeMe))
                }
            }
        }
    }

    @Test
    fun deleteFromValProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedVal>(ANNOTATED_CLASS).apply {
                getMethod("getUnwantedVal").also { method ->
                    assertNotNull(method.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedVal>(ANNOTATED_CLASS).apply {
                getMethod("getUnwantedVal").also { method ->
                    assertNull(method.getAnnotation(removeMe))
                }
            }
        }
    }

    @Test
    fun deleteFromVarProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedVar>(ANNOTATED_CLASS).apply {
                getMethod("getUnwantedVar").also { method ->
                    assertNotNull(method.getAnnotation(removeMe))
                }
                getMethod("setUnwantedVar", String::class.java).also { method ->
                    assertNotNull(method.getAnnotation(removeMe))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            val removeMe = cl.load<Annotation>(REMOVE_ME_CLASS)
            cl.load<HasUnwantedVar>(ANNOTATED_CLASS).apply {
                getMethod("getUnwantedVar").also { method ->
                    assertNull(method.getAnnotation(removeMe))
                }
                getMethod("setUnwantedVar", String::class.java).also { method ->
                    assertNull(method.getAnnotation(removeMe))
                }
            }
        }
    }
}
