package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BasicEnumTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "basic-enum").build();
    }

    @Test
    void testBasicEnum() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public final class net.corda.example.BasicEnum extends java.lang.Enum",
            "  public static net.corda.example.BasicEnum valueOf(String)",
            "  public static net.corda.example.BasicEnum[] values()",
            "##"
        );
    }
}
