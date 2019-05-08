package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;

public class GenerateApiTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-api")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    public void testGenerateApi() throws IOException {
        assertThat(testProject.getOutcomeOf("jar")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("scanApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines())
            .contains(
                "public class net.corda.example.SimpleClass extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
