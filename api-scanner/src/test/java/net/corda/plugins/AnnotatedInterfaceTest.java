package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class AnnotatedInterfaceTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-interface");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedInterface() throws IOException {
        assertEquals(
            "@DoNotImplement\n" +
            "@AlsoInherited\n" +
            "@IsInherited\n" +
            "@NotInherited\n" +
            "public interface net.corda.example.HasInheritedAnnotation\n" +
            "##\n" +
            "@AlsoInherited\n" +
            "@IsInherited\n" +
            "public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation\n" +
            "##", testProject.getApi());
    }
}
