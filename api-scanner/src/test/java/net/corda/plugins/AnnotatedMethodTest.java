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
import java.nio.file.Path;

import static org.junit.Assert.*;

public class AnnotatedMethodTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("annotated-method/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedMethod() throws IOException {
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

        Path api = CopyUtils.pathOf(testProjectDir, "build", "api", "annotated-method.txt");
        assertTrue(api.toFile().isFile());
        assertEquals(
            "public class net.corda.example.HasAnnotatedMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @net.corda.example.Visible public void hasAnnotation()\n" +
            "##\n" +
            "public @interface net.corda.example.Visible\n" +
            "##\n", CopyUtils.toString(api));
    }
}
