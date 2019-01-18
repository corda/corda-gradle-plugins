package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateApiTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "generate-api")
            .withTaskName("generateApi");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testGenerateApi() throws IOException {
        assertThat(testProject.getApiLines())
            .contains(
                "public class net.corda.example.SimpleClass extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
