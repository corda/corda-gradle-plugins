package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;

public class UnscannedJarTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "unscanned-jar")
            .withExpectedOutcome(NO_SOURCE)
            .build();
    }

    @Test
    public void testUnscannedJar() {
        assertThat(testProject.getOutcomeOf("jar")).isNull();
        assertThat(testProject.getOutcomeOf("otherJar")).isNull();
    }
}
