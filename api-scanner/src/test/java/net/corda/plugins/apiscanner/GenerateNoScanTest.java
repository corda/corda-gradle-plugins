package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

class GenerateNoScanTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-no-scan")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    void testGenerateNoScan() throws IOException {
        assertThat(testProject.getOutcomeOf("jar")).isNull();
        assertThat(testProject.getOutcomeOf("scanApi")).isNull();
        assertThat(testProject.getOutcomeOf("generateApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines()).isEmpty();
    }
}
