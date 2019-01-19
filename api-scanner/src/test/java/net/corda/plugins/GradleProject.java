package net.corda.plugins;

import org.assertj.core.api.AbstractPathAssert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.Assert.*;

/**
 * JUnit rule to execute the scanApi Gradle task. This rule should be chained with TemporaryFolder.
 */
@SuppressWarnings("WeakerAccess")
public class GradleProject implements TestRule {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", "");

    private final TemporaryFolder projectDir;
    private final String name;
    private String taskName = "scanApi";
    private TaskOutcome expectedOutcome = SUCCESS;

    private BuildResult result;
    private String output;
    private Path api;

    public GradleProject(TemporaryFolder projectDir, String name) {
        this.projectDir = projectDir;
        this.name = name;
        this.output = "";
    }

    public GradleProject withTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public GradleProject withExpectedOutcome(TaskOutcome outcome) {
        this.expectedOutcome = outcome;
        return this;
    }

    public Path getApi() {
        return api;
    }

    public List<String> getApiLines() throws IOException {
        // Files.readAllLines() uses UTF-8 by default.
        return (api == null) ? emptyList() : Files.readAllLines(api);
    }

    public String getApiText() throws IOException {
        return String.join("\n", getApiLines());
    }

    public String getOutput() {
        return output;
    }

    public TaskOutcome getOutcomeOf(String taskName) {
        BuildTask task = result.task(":" + taskName);
        return task == null ? null : task.getOutcome();
    }

    private void validate(AbstractPathAssert<?> check) {
        if (expectedOutcome == SUCCESS) {
            check.isRegularFile();
        } else {
            check.doesNotExist();
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                installResource(projectDir, name + "/build.gradle");
                installResource(projectDir, "repositories.gradle");
                installResource(projectDir, "settings.gradle");
                installResource(projectDir, "gradle.properties");

                result = GradleRunner.create()
                    .withProjectDir(projectDir.getRoot())
                    .withArguments(getGradleArgsForTasks(taskName))
                    .withPluginClasspath()
                    .build();
                output = result.getOutput();
                System.out.println(output);

                assertEquals(expectedOutcome, getOutcomeOf(taskName));

                api = pathOf(projectDir, "build", "api", name + ".txt");
                validate(assertThat(api));
            }
        };
    }

    public static Path pathOf(@Nonnull TemporaryFolder folder, String... elements) {
        return Paths.get(folder.getRoot().getAbsolutePath(), elements);
    }

    public static List<String> getGradleArgsForTasks(@Nonnull String... taskNames) {
        List<String> args = new ArrayList<>(taskNames.length + 4);
        Collections.addAll(args, taskNames);
        Collections.addAll(args, "--info", "--stacktrace");
        if (!TEST_GRADLE_USER_HOME.isEmpty()) {
            Collections.addAll(args, "-g", TEST_GRADLE_USER_HOME);
        }
        return args;
    }
}
