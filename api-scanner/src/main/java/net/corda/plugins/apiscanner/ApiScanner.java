package net.corda.plugins.apiscanner;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import javax.annotation.Nonnull;
import java.util.Objects;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME;

@SuppressWarnings("unused")
public class ApiScanner implements Plugin<Project> {
    private static final String CLASSIFIER_PROPERTY_NAME = "cordaScanApiClassifier";
    private static final String DEFAULT_CLASSIFIER = "";
    private static final String SCAN_TASK_NAME = "scanApi";
    static final String GROUP_NAME = "Corda API";

    /**
     * Identify the Gradle Jar task for the primary Maven artifact,
     * and generate API documentation for it.
     * @param project Current project.
     */
    @Override
    public void apply(@Nonnull Project project) {
        project.getLogger().info("Applying API scanner to {}", project.getName());
        project.getPluginManager().apply(JavaPlugin.class);

        String targetClassifier = (String) project.findProperty(CLASSIFIER_PROPERTY_NAME);
        if (targetClassifier == null) {
            targetClassifier = DEFAULT_CLASSIFIER;
        }

        ScannerExtension extension = project.getExtensions().create(SCAN_TASK_NAME, ScannerExtension.class, targetClassifier);

        // Register the scanning task lazily, so that it will be configured after the project has been evaluated.
        project.getLogger().info("Adding {} task to {}", SCAN_TASK_NAME, project.getName());
        TaskProvider<ScanApi> scanProvider = project.getTasks().register(SCAN_TASK_NAME, ScanApi.class, scanTask -> {
            TaskCollection<Jar> jarTasks = project.getTasks()
                .withType(Jar.class)
                .matching(jarTask ->
                     matches(jarTask.getArchiveClassifier(), extension.getTargetClassifier()) && jarTask.isEnabled()
                );
            FileCollection jarSources = project.files(jarTasks);

            scanTask.setClasspath(project.getConfigurations().getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME));
            // Automatically creates a dependency on jar tasks.
            scanTask.setSources(jarSources);
            scanTask.setExcludePackages(extension.getExcludePackages());
            scanTask.setExcludeClasses(extension.getExcludeClasses());
            scanTask.setExcludeMethods(extension.getExcludeMethods());
            scanTask.setVerbose(extension.getVerbose());
            scanTask.setEnabled(extension.isEnabled());
        });

        // Declare this ScanApi task to be a dependency of any GenerateApi tasks belonging to any of our ancestors.
        Project target = project;
        while (target != null) {
            target.getTasks().withType(GenerateApi.class)
                .configureEach(generateTask -> generateTask.dependsOn(scanProvider));
            target = target.getParent();
        }
    }

    private static boolean matches(@Nonnull Provider<String> a, @Nonnull Provider<String> b) {
        return Objects.equals(a.getOrNull(), b.getOrNull());
    }
}
