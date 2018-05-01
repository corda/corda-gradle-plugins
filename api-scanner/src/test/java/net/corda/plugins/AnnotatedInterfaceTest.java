package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

public class AnnotatedInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedInterface() throws IOException {
        assertThat(Files.readAllLines(testProject.getApi())).containsOnlyOnce(
            "@net.corda.annotation.AlsoInherited @net.corda.annotation.IsInherited @net.corda.annotation.NotInherited public interface net.corda.example.HasInheritedAnnotation",
            "@net.corda.annotation.AlsoInherited @net.corda.annotation.IsInherited public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation"
        );
    }
}
