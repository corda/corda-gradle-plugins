package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InheritedFieldTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "inherited-field").build();
    }

    @Test
    void testInheritedField() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ChildFieldClass extends net.corda.example.ParentFieldClass",
            "  public <init>()",
            "  public static final long CHILD_NUMBER = 101",
            "  protected boolean childFlag",
            "##"
        ).containsSequence(
            "public class net.corda.example.ParentFieldClass extends java.lang.Object",
            "  public <init>()",
            "  public static final String PARENT_MESSAGE = \"Hello World!\"",
            "  protected double parentMultiplier",
            "##"
        );
    }
}
