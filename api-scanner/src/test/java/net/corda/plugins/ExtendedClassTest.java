package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class ExtendedClassTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "extended-class");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testExtendedClass() throws IOException {
        assertEquals(
            "public class net.corda.example.ExtendedClass extends java.io.FilterInputStream\n" +
            "  public <init>(java.io.InputStream)\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}
