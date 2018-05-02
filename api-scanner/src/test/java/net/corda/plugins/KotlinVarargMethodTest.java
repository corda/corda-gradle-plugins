package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class KotlinVarargMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-vararg-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testKotlinVarargMethod() throws IOException {
        assertEquals("public interface net.corda.example.KotlinVarargMethod\n" +
            "  public abstract void action(String[]...)\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}