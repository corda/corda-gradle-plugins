package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

class GenerateChildProjectTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-child-project")
            .withSubResource("child-project/build.gradle")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    void testGenerateChildProject() throws IOException {
        assertThat(testProject.getOutcomeOf("child-project:jar")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("child-project:scanApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("scanApi")).isNull();
        assertThat(testProject.getOutcomeOf("generateApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines())
            .contains(
                "public class net.corda.example.ChildClass extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
