package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import static org.gradle.testkit.runner.TaskOutcome.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class AnnotatedMethodTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("annotated-method/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedMethod() throws IOException {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(getGradleArgsForTasks("scanApi"))
            .withPluginClasspath()
            .build();
        String output = result.getOutput();
        System.out.println(output);

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(SUCCESS, scanApi.getOutcome());

        Path api = pathOf(testProjectDir, "build", "api", "annotated-method.txt");
        assertThat(api).isRegularFile();
        assertEquals(
            "public @interface net.corda.example.A\n" +
            "##\n" +
            "public @interface net.corda.example.B\n" +
            "##\n" +
            "public @interface net.corda.example.C\n" +
            "##\n" +
            "public class net.corda.example.HasAnnotatedMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @A\n" +
            "  @B\n" +
            "  @C\n" +
            "  public void hasAnnotation()\n" +
            "##\n" +
            "public class net.corda.example.HasDeprecatedMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public void isDeprecated()\n" +
            "##", CopyUtils.toString(api));
    }
}
