package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class AnnotatedClassTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "annotated-class").build();
    }

    @Test
    public void testAnnotatedClass() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public class net.corda.example.HasInheritedAnnotation extends java.lang.Object")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public class net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@DoNotImplement",
                "@AnAnnotation",
                "public class net.corda.example.DoNotImplementAnnotation extends java.lang.Object");
    }
}
