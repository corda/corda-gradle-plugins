package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class KotlinVarargMethodTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-vararg-method").build();
    }

    private static final String[] expectedInterfaceWithVarargMethod = {
        "public interface net.corda.example.KotlinVarargMethod",
        "  public abstract void action(int...)",
        "##"
    };

    private static final String[] expectedInterfaceWithArrayVarargMethod = {
            "public interface net.corda.example.KotlinVarargArrayMethod",
            "  public abstract void action(String[]...)",
            "##"
    };

    @Test
    public void testKotlinVarargMethod() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedInterfaceWithVarargMethod)
            .containsSequence(expectedInterfaceWithArrayVarargMethod);
    }
}