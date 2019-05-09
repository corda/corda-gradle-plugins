package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class FieldWithInternalAnnotationTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "field-internal-annotation").build();
    }

    @Test
    public void testFieldWithInternalAnnotations() throws IOException {
        assertThat(testProject.getOutput())
            .contains("net.corda.example.field.InvisibleAnnotation")
            .contains("net.corda.example.field.LocalInvisibleAnnotation");
        assertEquals("public class net.corda.example.field.HasVisibleField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public String hasInvisibleAnnotations\n" +
            "##", testProject.getApiText());
    }
}
