package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.Assert.*;

/**
 * JUnit rule to execute the scanApi Gradle task. This rule should be chained with TemporaryFolder.
 */
public class GradleProject implements TestRule {
    private final TemporaryFolder projectDir;
    private final String name;

    private String output;
    private Path api;

    public GradleProject(TemporaryFolder projectDir, String name) {
        this.projectDir = projectDir;
        this.name = name;
    }

    public Path getApi() {
        return api;
    }

    public String getOutput() {
        return output;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                installResource(projectDir, name + "/build.gradle");
                installResource(projectDir, "gradle.properties");

                BuildResult result = GradleRunner.create()
                    .withProjectDir(projectDir.getRoot())
                    .withArguments(getGradleArgsForTasks("scanApi"))
                    .withPluginClasspath()
                    .build();
                output = result.getOutput();
                System.out.println(output);

                BuildTask scanApi = result.task(":scanApi");
                assertNotNull(scanApi);
                assertEquals(SUCCESS, scanApi.getOutcome());

                api = pathOf(projectDir, "build", "api", name + ".txt");
                assertThat(api).isRegularFile();
                base.evaluate();
            }
        };
    }
}
