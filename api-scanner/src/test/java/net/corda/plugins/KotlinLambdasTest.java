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

public class KotlinLambdasTest {
    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("kotlin-lambdas/build.gradle", buildFile);
    }

    @Test
    public void testKotlinLambdas() throws IOException {
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

        assertTrue(output.contains("net.corda.example.LambdaExpressions$testing$$inlined$schedule$1"));

        File api = new File(testProjectDir.getRoot(), "build/api/kotlin-lambdas.txt");
        assertTrue(api.isFile());
        assertEquals("public final class net.corda.example.LambdaExpressions extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public final void testing(kotlin.Unit)\n" +
            "##\n", CopyUtils.toString(api));
    }
}
