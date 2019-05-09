package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedInterfaceTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "extended-interface").build();
    }

    @Test
    public void testExtendedInterface() throws IOException {
        assertEquals(
            "public interface net.corda.example.ExtendedInterface extends java.util.concurrent.Future\n" +
            "  public abstract String getName()\n" +
            "  public abstract void setName(String)\n" +
            "##", testProject.getApiText());
    }
}
