package net.corda.plugins;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KotlinEnumTest {
    private static GradleProject testProject;

    @BeforeAll
    static void setup(@TempDir Path testProjectDir) throws IOException {
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
