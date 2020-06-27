package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;

class GenerateEmptyApiTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-empty-api")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    void testGenerateEmptyApi() throws IOException {
        assertThat(testProject.getOutcomeOf("jar")).isNull();
        assertThat(testProject.getOutcomeOf("scanApi")).isEqualTo(NO_SOURCE);
        assertThat(testProject.getOutcomeOf("generateApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines()).isEmpty();
    }
}
