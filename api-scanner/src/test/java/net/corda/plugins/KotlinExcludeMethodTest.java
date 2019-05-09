package net.corda.plugins;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class KotlinExcludeMethodTest {
    private static GradleProject testProject;

    @BeforeAll
    public static void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-exclude-method").build();
    }

    @Test
    public void testFilteredMethodsAreExcluded() throws IOException {
        assertThat(testProject.getApiText())
                .contains("net.corda.example.ClassWithExtraConstructorGenerated")
                .doesNotContain("<init>(String, String, kotlin.jvm.internal.DefaultConstructorMarker)");
    }

}
