package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class MethodWithInternalAnnotationTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "method-internal-annotation").build();
    }

    @Test
    public void testMethodWithInternalAnnotations() throws IOException {
        assertThat(testProject.getOutput())
            .contains("net.corda.example.method.InvisibleAnnotation")
            .contains("net.corda.example.method.LocalInvisibleAnnotation");

        assertEquals("public class net.corda.example.method.HasVisibleMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public void hasInvisibleAnnotations()\n" +
            "##", testProject.getApiText());
    }
}
