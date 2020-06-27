package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class KotlinLegacyTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-legacy")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testLibraryIsScanned() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "@AnAnnotation",
                "public final class net.corda.example.LegacyApi extends java.lang.Object",
                "  public <init>()",
                "##"
            );
    }
}
