package net.corda.plugins;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class KotlinExcludeMethodTest {

    private static final TemporaryFolder testProjectDir = new TemporaryFolder();
    private static final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-exclude-method");

    @ClassRule
    public static final TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testFilteredMethodsAreExcluded() throws IOException {
        assertThat(testProject.getApiText())
                .contains("net.corda.example.ClassWithExtraConstructorGenerated")
                .doesNotContain("<init>(String, String, kotlin.jvm.internal.DefaultConstructorMarker)");
    }

}
