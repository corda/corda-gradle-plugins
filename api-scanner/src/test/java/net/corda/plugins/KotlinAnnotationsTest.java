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

public class KotlinAnnotationsTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("kotlin-annotations/build.gradle", buildFile);
    }

    @Test
    public void testKotlinAnnotations() throws IOException {
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

        File api = new File(testProjectDir.getRoot(), "build/api/kotlin-annotations.txt");
        assertTrue(api.isFile());
        assertEquals(
                "public final class net.corda.example.HasJvmStaticFunction extends java.lang.Object\n" +
                "  public <init>()\n" +
                "  @kotlin.jvm.JvmStatic public static final void doThing(String)\n" +
                "  public static final net.corda.example.HasJvmStaticFunction$Companion Companion\n" +
                "##\n" +
                "public static final class net.corda.example.HasJvmStaticFunction$Companion extends java.lang.Object\n" +
                "  @kotlin.jvm.JvmStatic public final void doThing(String)\n" +
                "##\n" +
                "public final class net.corda.example.HasOverloadedConstructor extends java.lang.Object\n" +
                "  public <init>()\n" +
                "  public <init>(String)\n" +
                "  public <init>(String, String)\n" +
                "  public <init>(String, String, int)\n" +
                "  @org.jetbrains.annotations.NotNull public final String getNotNullable()\n" +
                "  @org.jetbrains.annotations.Nullable public final String getNullable()\n" +
                "  public final int getNumber()\n" +
                "##\n", CopyUtils.toString(api));
    }
}
