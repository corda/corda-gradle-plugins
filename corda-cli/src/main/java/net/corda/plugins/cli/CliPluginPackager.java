package net.corda.plugins.cli;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;

public class CliPluginPackager implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        project.getTasks().named("CliPluginPackage", Jar.class, jar -> {
            jar.getArchiveBaseName().set("plugin");

            jar.manifest(manifest -> {
                Attributes attributes = manifest.getAttributes()
            });

            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            jar.from(main.getOutput());

            ConfigurationContainer runtimeConfigContainer = project.getConfigurations();
            Configuration runtimeConfig = runtimeConfigContainer.getByName("runtimeClasspath");

            jar.dependsOn(runtimeConfig);

            jar.from(runtimeConfig);
        });
    }
}
