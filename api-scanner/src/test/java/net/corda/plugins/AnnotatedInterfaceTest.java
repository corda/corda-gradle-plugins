package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class AnnotatedInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedInterface() throws IOException {
        assertThat(Files.readAllLines(testProject.getApi()))
            .containsSequence(
                "@DoNotImplement",
                "@AlsoInherited",
                "@IsInherited",
                "@NotInherited",
                "public interface net.corda.example.HasInheritedAnnotation")
            .containsSequence(
                "@AlsoInherited",
                "@IsInherited",
                "public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation"
            );
    }
}
