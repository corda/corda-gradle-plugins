package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcludePackageTest {
    private GradleProject testProject;

    @BeforeEach
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "exclude-package").build();
    }

    @Test
    void testExcludingEntirePackage() throws IOException {
        assertEquals("public class net.corda.example.wanted.WantedClass extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##", testProject.getApiText());

        // Make this explicit - the unwanted classes have NOT been included!
        assertThat(testProject.getApiText()).doesNotContain(
            "class net.corda.example.unwanted.UnwantedClass ",
            "class net.corda.example.unwanted.very.VeryUnwantedClass "
        );
    }
}
