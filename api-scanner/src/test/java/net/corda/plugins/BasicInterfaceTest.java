package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BasicInterfaceTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "basic-interface").build();
    }

    @Test
    public void testBasicInterface() throws IOException {
        assertEquals(
            "public interface net.corda.example.BasicInterface\n" +
            "  public abstract java.math.BigInteger getBigNumber()\n" +
            "##", testProject.getApiText());
    }
}
