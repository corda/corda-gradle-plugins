package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class AnnotatedClassTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-class");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedClass() throws IOException {
        assertEquals(
            "@DoNotImplement\n" +
            "@AlsoInherited\n" +
            "@IsInherited\n" +
            "@NotInherited\n" +
            "public class net.corda.example.HasInheritedAnnotation extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##\n" +
            "@AlsoInherited\n" +
            "@IsInherited\n" +
            "public class net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation\n" +
            "  public <init>()\n" +
            "##", testProject.getApi());
    }
}
