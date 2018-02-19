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

public class AnnotatedClassTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("annotated-class/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedClass() throws IOException {
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

        Path api = CopyUtils.pathOf(testProjectDir, "build", "api", "annotated-class.txt");
        assertTrue(api.toFile().isFile());
        assertEquals(
            "@net.corda.example.NotInherited @net.corda.example.IsInherited public class net.corda.example.HasInheritedAnnotation extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##\n" +
            "@net.corda.example.IsInherited public class net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation\n" +
            "  public <init>()\n" +
            "##\n" +
            "public @interface net.corda.example.IsInherited\n" +
            "##\n" +
            "public @interface net.corda.example.NotInherited\n" +
            "##\n", CopyUtils.toString(api));
    }
}
