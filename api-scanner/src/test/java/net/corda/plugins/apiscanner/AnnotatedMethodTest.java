package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class AnnotatedMethodTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "annotated-method").build();
    }

    @Test
    void testAnnotatedMethod() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "  @A",
                "  @B",
                "  @C",
                "  public void hasAnnotation()")
            //Should not include @Deprecated annotation
            .containsSequence(
                "public class net.corda.example.HasDeprecatedMethod extends java.lang.Object",
                "  public <init>()",
                "  public void isDeprecated()"
            );
    }
}
