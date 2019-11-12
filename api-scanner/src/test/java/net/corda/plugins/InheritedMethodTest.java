package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InheritedMethodTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "inherited-method").build();
    }

    @Test
    void testInheritedMethod() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public class net.corda.example.ChildMethodClass extends net.corda.example.ParentMethodClass",
            "  public <init>()",
            "  protected static String geChildName()",
            "  public int getChildNumber()",
            "##"
        ).containsSequence(
            "public class net.corda.example.ParentMethodClass extends java.lang.Object",
            "  public <init>()",
            "  public String getParentMessage()",
            "  protected static String getParentName()",
            "##"
        );
    }
}
