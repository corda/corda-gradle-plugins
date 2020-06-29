package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class AnnotatedInterfaceTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "annotated-interface").build();
    }

    @Test
    void testAnnotatedInterface() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public interface net.corda.example.HasInheritedAnnotation",
                "##")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation",
                "##")
            .containsSequence(
                "@DoNotImplement",
                "@AnAnnotation",
                "public interface net.corda.example.DoNotImplementAnnotation",
                "##"
            ).containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public class net.corda.example.BaseClass extends java.lang.Object implements net.corda.example.InheritingAnnotations",
                "  public <init>()",
                "##"
            );
    }
}
