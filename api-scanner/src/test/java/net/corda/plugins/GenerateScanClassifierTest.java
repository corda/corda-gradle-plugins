package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

class GenerateScanClassifierTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "generate-scan-classifier")
            .withTaskName("generateApi")
            .build();
    }

    @Test
    void testApiScanWithClassifier() throws IOException {
        assertThat(testProject.getOutcomeOf("jar")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("scanApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getOutcomeOf("generateApi")).isEqualTo(SUCCESS);
        assertThat(testProject.getApiLines())
            .contains(
                "public class net.corda.example.WhenScanHasClassifier extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
