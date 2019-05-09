package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BasicAnnotationTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "basic-annotation").build();
    }

    @Test
    public void testBasicAnnotation() throws IOException {
        assertEquals(
            "public @interface net.corda.example.BasicAnnotation\n" +
            "##", testProject.getApiText());
    }
}
