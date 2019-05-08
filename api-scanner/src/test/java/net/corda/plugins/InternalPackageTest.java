package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class InternalPackageTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "internal-package").build();
    }

    @Test
    public void testInternalPackageIsIgnored() throws IOException {
        assertThat(testProject.getOutput()).contains("net.corda.internal.InvisibleClass");
        assertEquals(
            "public class net.corda.VisibleClass extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", testProject.getApiText());
    }
}
