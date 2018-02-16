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

public class AnnotatedClassesTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("annotated-classes/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedClasses() throws IOException {
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

        File api = new File(testProjectDir.getRoot(), "build/api/annotated-classes.txt");
        assertTrue(api.isFile());
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
