package net.corda.plugins;

import org.gradle.testkit.runner.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.Assert.*;

public class KotlinVarargMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-vararg-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testKotlinVarargMethod() throws IOException {
        assertEquals("public interface net.corda.example.KotlinVarargMethod\n" +
            "  public abstract void action(Object...)\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}