package net.corda.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
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
            ConfigurableFileCollection jarSources = project.files();

            project.getTasks().withType(Jar.class, jarTask -> {
                if (jarTask.getClassifier().isEmpty() && jarTask.isEnabled()) {
                    jarSources.from(jarTask);
                }
            });

            scanTask.setClasspath(compilationClasspath(project.getConfigurations()));
            // Automatically creates a dependency on jar tasks.
            scanTask.setSources(jarSources);
            scanTask.setExcludeClasses(extension.getExcludeClasses());
            scanTask.setExcludeMethods(extension.getExcludeMethods());
            scanTask.setVerbose(extension.isVerbose());
            scanTask.setEnabled(extension.isEnabled());
        });

        // Declare this ScanApi task to be a dependency of any GenerateApi tasks belonging to any of our ancestors.
        project.getRootProject().getTasks().withType(GenerateApi.class, generateTask -> {
            if (isAncestorOf(generateTask.getProject(), project)) {
                generateTask.dependsOn(scanProvider);
            }
        });
    }

    /*
     * Recurse through a child project's parents until we reach the root,
     * and return true iff we find our target project along the way.
     */
    private static boolean isAncestorOf(Project target, Project child) {
        Project p = child;
        while (p != null) {
            if (p == target) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    private static FileCollection compilationClasspath(ConfigurationContainer configurations) {
        return configurations.getByName("compile")
                .plus(configurations.getByName("compileOnly"));
    }
}
