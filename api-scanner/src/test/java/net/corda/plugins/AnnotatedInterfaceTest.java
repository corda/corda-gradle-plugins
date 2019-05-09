package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class AnnotatedInterfaceTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "annotated-interface").build();
    }

    @Test
    public void testAnnotatedInterface() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public interface net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@DoNotImplement",
                "@AnAnnotation",
                "public interface net.corda.example.DoNotImplementAnnotation"
            );
    }
}
