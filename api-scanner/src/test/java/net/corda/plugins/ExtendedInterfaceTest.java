package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendedInterfaceTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "extended-interface").build();
    }

    @Test
    void testExtendedInterface() throws IOException {
        assertThat(testProject.getApiLines()).containsSequence(
            "public interface net.corda.example.ExtendedInterface extends java.lang.Appendable, java.lang.Comparable, java.util.concurrent.Future",
            "  public abstract String getName()",
            "  public abstract void setName(String)",
            "##"
        );
    }
}
