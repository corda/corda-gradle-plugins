package net.corda.plugins.apiscanner;

import org.assertj.core.api.AbstractPathAssert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static net.corda.plugins.apiscanner.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes the API Scanner plugin in a test Gradle project.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class GradleProject {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", "");

    private final Path projectDir;
    private final String name;
    private String taskName = "scanApi";
    private TaskOutcome expectedOutcome = SUCCESS;

    private BuildResult result;
    private String output;
    private Path api;

    public GradleProject(Path projectDir, String name) {
        this.projectDir = projectDir;
        this.name = name;
        this.output = "";
    }

    public GradleProject withResource(String resourceName) throws IOException {
        installResource(projectDir, resourceName);
        return this;
    }

    public GradleProject withSubResource(String resourceName) throws IOException {
        installResource(subDirectoryFor(resourceName), name + '/' + resourceName);
        return this;
    }

    private Path subDirectoryFor(@Nonnull String resourceName) throws IOException {
        Path directory = projectDir;
        int startIdx = 0;
        while (true) {
            int endIdx = resourceName.indexOf('/', startIdx);
            if (endIdx == -1) {
                break;
            }
            directory = Files.createDirectory(directory.resolve(resourceName.substring(startIdx, endIdx)));
            startIdx = endIdx + 1;
        }
        return directory;
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

    private void configureGradle(Function<GradleRunner, BuildResult> builder, String[] args) throws IOException {
        installResource(projectDir, name + "/build.gradle");
        installResource(projectDir, "gradle.properties");
        if (!installResource(projectDir, name + "/settings.gradle")) {
            installResource(projectDir, "settings.gradle");
        }

        GradleRunner runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(getGradleArgs(args))
            .withPluginClasspath()
            .withDebug(true);
        result = builder.apply(runner);
        output = result.getOutput();
        System.out.println(output);
    }

    public GradleProject build(String... args) throws IOException {
        configureGradle(GradleRunner::build, args);
        assertEquals(expectedOutcome, getOutcomeOf(taskName));

        api = pathOf(projectDir, "build", "api", name + ".txt");
        validate(assertThat(api));
        return this;
    }

    public GradleProject buildAndFail(String... args) throws IOException {
        configureGradle(GradleRunner::buildAndFail, args);
        return this;
    }

    @Nonnull
    private static Path pathOf(@Nonnull Path folder, String... elements) {
        return Paths.get(folder.toAbsolutePath().toString(), elements);
    }

    @Nonnull
    private List<String> getGradleArgs(@Nonnull String[] extraArgs) {
        List<String> args = new ArrayList<>(extraArgs.length + 5);
        Collections.addAll(args, "--info", "--stacktrace");
        if (!TEST_GRADLE_USER_HOME.isEmpty()) {
            Collections.addAll(args, "-g", TEST_GRADLE_USER_HOME);
        }
        if (taskName != null) {
            args.add(taskName);
        }
        Collections.addAll(args, extraArgs);
        return args;
    }
}
