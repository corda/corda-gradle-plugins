package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-class");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedClass() throws IOException {
        assertThat(Files.readAllLines(testProject.getApi()))
            .containsSequence(
                "@DoNotImplement",
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public class net.corda.example.HasInheritedAnnotation extends java.lang.Object")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public class net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation");
    }
}
