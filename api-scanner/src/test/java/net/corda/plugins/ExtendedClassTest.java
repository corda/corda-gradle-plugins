package net.corda.plugins;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class ExtendedClassTest {
    private static GradleProject testProject;

    @BeforeAll
    public static void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "extended-class").build();
    }

    @Test
    public void testExtendedClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ExtendedClass extends java.io.FilterInputStream",
            "  public <init>(java.io.InputStream)",
            "##");
    }

    @Test
    public void testImplementingClass() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ImplementingClass extends java.lang.Object implements java.io.Closeable",
            "  public <init>()",
            "  public void close()",
            "##");
    }
}
