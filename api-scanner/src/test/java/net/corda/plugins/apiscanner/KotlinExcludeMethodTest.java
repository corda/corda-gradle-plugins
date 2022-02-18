package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class KotlinExcludeMethodTest {
    private GradleProject testProject;

    @BeforeAll
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-exclude-method")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testFilteredMethodsAreExcluded() throws IOException {
        assertThat(testProject.getApiText())
            .contains("net.corda.example.ClassWithExtraConstructorGenerated")
            .doesNotContain("<init>(String, String, kotlin.jvm.internal.DefaultConstructorMarker)");
    }

}
