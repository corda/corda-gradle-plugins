package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class InternalFieldTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "internal-field").build();
    }

    @Test
    public void testInternalField() throws IOException {
        assertEquals(
            "public class net.corda.example.WithInternalField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", testProject.getApiText());
    }
}
