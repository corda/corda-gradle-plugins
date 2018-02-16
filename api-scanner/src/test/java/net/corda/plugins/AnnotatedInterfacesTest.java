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

public class AnnotatedInterfacesTest {
    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("annotated-interfaces/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedInterfaces() throws IOException {
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

        File api = new File(testProjectDir.getRoot(), "build/api/annotated-interfaces.txt");
        assertTrue(api.isFile());
        assertEquals(
            "@net.corda.example.NotInherited @net.corda.example.IsInherited public interface net.corda.example.HasInheritedAnnotation\n" +
            "##\n" +
            "@net.corda.example.IsInherited public interface net.corda.example.InheritingAnnotations extends net.corda.example.HasInheritedAnnotation\n" +
            "##\n" +
            "public @interface net.corda.example.IsInherited\n" +
            "##\n" +
            "public @interface net.corda.example.NotInherited\n" +
            "##\n", CopyUtils.toString(api));
    }
}
