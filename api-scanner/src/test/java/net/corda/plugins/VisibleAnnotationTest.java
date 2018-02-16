package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class VisibleAnnotationTest {
    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("visible-annotation/build.gradle", buildFile);
    }

    @Test
    public void testVisibleAnnotation() throws IOException {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("scanApi", "--info")
            .withPluginClasspath()
            .build();
        String output = result.getOutput();
        System.out.println(output);

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(TaskOutcome.SUCCESS, scanApi.getOutcome());

        File api = new File(testProjectDir.getRoot(), "build/api/visible-annotation.txt");
        assertTrue(api.isFile());
        assertEquals(
            "public @interface net.corda.example.Visible\n" +
            "##\n" +
            "@net.corda.example.Visible public class net.corda.example.WithVisibleAnnotation extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @net.corda.example.Visible public void hasVisibleAnnotation()\n" +
            "##\n", CopyUtils.toString(api));
    }
}
