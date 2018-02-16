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

public class ExtendedInterfaceTest {
    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        CopyUtils.copyResourceTo("extended-interface/build.gradle", buildFile);
    }

    @Test
    public void testExtendedInterface() throws IOException {
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

        File api = new File(testProjectDir.getRoot(), "build/api/extended-interface.txt");
        assertTrue(api.isFile());
        assertEquals(
            "public interface net.corda.example.ExtendedInterface extends java.util.concurrent.Future\n" +
            "  public abstract String getName()\n" +
            "  public abstract void setName(String)\n" +
            "##\n", CopyUtils.toString(api));
    }
}
