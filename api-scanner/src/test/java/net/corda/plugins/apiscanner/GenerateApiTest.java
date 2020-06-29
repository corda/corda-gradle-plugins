package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;

class GenerateApiTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-api")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    void testGenerateApi() throws IOException {
        assertThat(testProject.getOutcomeOf("jar")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("scanApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("generateApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines())
            .contains(
                "public class net.corda.example.SimpleClass extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
