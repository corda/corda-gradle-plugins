package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

public class AnnotatedMethodTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "annotated-method");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testAnnotatedMethod() throws IOException {
        assertThat(Files.readAllLines(testProject.getApi())).containsOnlyOnce(
            "public class net.corda.example.HasAnnotatedMethod extends java.lang.Object",
            "  @net.corda.example.A @net.corda.example.B @net.corda.example.C public void hasAnnotation()"
        );
    }
}
