package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class KotlinLibraryTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-library")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testLibraryIsScanned() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AnAnnotation",
                "public final class net.corda.example.ExampleApi extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
