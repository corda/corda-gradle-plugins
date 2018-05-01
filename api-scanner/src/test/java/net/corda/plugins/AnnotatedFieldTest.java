package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class AnnotatedFieldTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-field");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedField() throws IOException {
        assertEquals(
            "public @interface net.corda.example.A\n" +
            "##\n" +
            "public @interface net.corda.example.B\n" +
            "##\n" +
            "public @interface net.corda.example.C\n" +
            "##\n" +
            "public class net.corda.example.HasAnnotatedField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @A\n" +
            "  @B\n" +
            "  @C\n" +
            "  public static final String ANNOTATED_FIELD = \"<string-value>\"\n" +
            "##", testProject.getApi());
    }
}
