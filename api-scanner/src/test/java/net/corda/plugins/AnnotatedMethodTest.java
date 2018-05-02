package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class AnnotatedMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedMethod() throws IOException {
        assertEquals(
            "public @interface net.corda.example.A\n" +
            "##\n" +
            "public @interface net.corda.example.B\n" +
            "##\n" +
            "public @interface net.corda.example.C\n" +
            "##\n" +
            "public class net.corda.example.HasAnnotatedMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @A\n" +
            "  @B\n" +
            "  @C\n" +
            "  public void hasAnnotation()\n" +
            "##\n" +
            "public class net.corda.example.HasDeprecatedMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public void isDeprecated()\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}
