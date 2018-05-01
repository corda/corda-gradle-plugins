package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class InternalFieldTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "internal-field");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testInternalField() throws IOException {
        assertEquals(
            "public class net.corda.example.WithInternalField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}
