package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.fileMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(PER_CLASS)
class DeleteTypeAliasFromFileTest {
    private companion object {
        private const val TYPEALIAS_CLASS = "net.corda.gradle.FileWithTypeAlias"
    }

    private lateinit var testProject: JarFilterProject
    private lateinit var sourceClasses: List<String>
    private lateinit var filteredClasses: List<String>

    @BeforeAll
    fun setup(@TempDir testProjectDir: Path) {
        testProject = JarFilterProject(testProjectDir, "delete-file-typealias").build()
        sourceClasses = testProject.sourceJar.getClassNames(TYPEALIAS_CLASS)
        filteredClasses = testProject.filteredJar.getClassNames(TYPEALIAS_CLASS)
    }

    @Test
    fun deleteTypeAlias() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            val metadata = cl.load<Any>(TYPEALIAS_CLASS).fileMetadata
            assertThat(metadata.typeAliasNames)
                .containsExactlyInAnyOrder("FileWantedType", "FileUnwantedType")
        }
        classLoaderFor(testProject.filteredJar).use { cl ->
            val metadata = cl.load<Any>(TYPEALIAS_CLASS).fileMetadata
            assertThat(metadata.typeAliasNames)
                .containsExactly("FileWantedType")
        }
    }
}