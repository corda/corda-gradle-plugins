package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class KotlinInternalAnnotationTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-internal-annotation")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testKotlinInternalAnnotation() throws IOException {
        assertThat(testProject.getOutput()).contains("net.corda.example.kotlin.CordaInternal");
        assertEquals(
            "public final class net.corda.example.kotlin.AnnotatedClass extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", testProject.getApiText());
    }
}
