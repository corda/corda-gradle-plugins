package net.corda.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import javax.annotation.Nonnull;

public class ApiScanner implements Plugin<Project> {

    /**
     * Identify the Gradle Jar tasks creating jars
     * without Maven classifiers, and generate API
     * documentation for them.
     * @param project Current project.
     */
    @Override
    public void apply(@Nonnull Project project) {
        project.getLogger().info("Applying API scanner to {}", project.getName());

        ScannerExtension extension = project.getExtensions().create("scanApi", ScannerExtension.class);

        // Register the scanning task lazily, so that it will be configured after the project has been evaluated.
        project.getLogger().info("Adding scanApi task to {}", project.getName());
        TaskProvider<ScanApi> scanProvider = project.getTasks().register("scanApi", ScanApi.class, scanTask -> {
            TaskCollection<Jar> jarTasks = project.getTasks()
                .withType(Jar.class)
                .matching(jarTask -> jarTask.getClassifier().isEmpty() && jarTask.isEnabled());
            FileCollection jarSources = project.files(jarTasks);

            scanTask.setClasspath(compilationClasspath(project.getConfigurations()));
            // Automatically creates a dependency on jar tasks.
            scanTask.setSources(jarSources);
            scanTask.setExcludeClasses(extension.getExcludeClasses());
            scanTask.setExcludeMethods(extension.getExcludeMethods());
            scanTask.setVerbose(extension.isVerbose());
            scanTask.setEnabled(extension.isEnabled());
        });

        // Declare this ScanApi task to be a dependency of any GenerateApi tasks belonging to any of our ancestors.
        Project target = project;
        while (target != null) {
            target.getTasks().withType(GenerateApi.class, generateTask -> {
                generateTask.dependsOn(scanProvider);
            });
            target = target.getParent();
        }
    }

    private static FileCollection compilationClasspath(ConfigurationContainer configurations) {
        return configurations.getByName("compileClasspath");
    }
}
