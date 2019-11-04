package net.corda.plugins;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ExtendedClassTest {
    private static GradleProject testProject;

    @BeforeAll
    static void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "extended-class").build();
    }

    @Test
    void testExtendedClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ExtendedClass extends java.io.FilterInputStream",
            "  public <init>(java.io.InputStream)",
            "##");
    }

    @Test
    void testImplementingClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ImplementingClass extends java.lang.Object implements java.io.Closeable, java.lang.AutoCloseable",
            "  public <init>()",
            "  public void close()",
            "##");
    }
}
