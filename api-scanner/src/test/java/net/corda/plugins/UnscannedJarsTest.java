package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static net.corda.plugins.CopyUtils.installResource;
import static net.corda.plugins.GradleProject.getGradleArgsForTasks;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;
import static org.junit.Assert.*;

public class UnscannedJarsTest {

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        installResource(testProjectDir, "unscanned-jars/build.gradle");
        installResource(testProjectDir, "repositories.gradle");
        installResource(testProjectDir, "settings.gradle");
        installResource(testProjectDir, "gradle.properties");
    }

    @Test
    public void testUnscannedJars() {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(getGradleArgsForTasks("scanApi"))
            .withPluginClasspath()
            .build();
        System.out.println(result.getOutput());

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(NO_SOURCE, scanApi.getOutcome());
    }
}
