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
class KotlinEnumTest {
    private GradleProject testProject;

    @BeforeAll
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-enum")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testKotlinEnum() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "public final class net.corda.example.ExampleKotlinEnum extends java.lang.Enum",
                "  public static net.corda.example.ExampleKotlinEnum valueOf(String)",
                "  public static net.corda.example.ExampleKotlinEnum[] values()",
                "##"
            );
    }
}
