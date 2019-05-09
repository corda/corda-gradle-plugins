package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VarargMethodTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "vararg-method").build();
    }

    @Test
    public void testVarargMethod() throws IOException {
        assertEquals("public interface net.corda.example.VarargMethod\n" +
            "  public abstract void action(Object...)\n" +
            "##", testProject.getApiText());
    }
}