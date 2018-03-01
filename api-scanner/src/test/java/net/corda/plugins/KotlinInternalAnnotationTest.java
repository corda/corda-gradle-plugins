package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.Assert.*;

public class KotlinInternalAnnotationTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("kotlin-internal-annotation/build.gradle", buildFile);
    }

    @Test
    public void testKotlinInternalAnnotation() throws IOException {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("scanApi", "--info")
            .withPluginClasspath()
            .build();
        String output = result.getOutput();
        System.out.println(output);

        assertTrue(output.contains("net.corda.kotlin.CordaInternal"));

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(SUCCESS, scanApi.getOutcome());

        Path api = CopyUtils.pathOf(testProjectDir, "build", "api", "kotlin-internal-annotation.txt");
        assertTrue(api.toFile().isFile());
        assertEquals(
            "public final class net.corda.kotlin.AnnotatedClass extends java.lang.Object\n" +
            "  public <init>()\n" +
            "##\n", CopyUtils.toString(api));
    }
}
