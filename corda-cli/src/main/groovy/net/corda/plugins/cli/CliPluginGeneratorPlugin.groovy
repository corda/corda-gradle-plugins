package net.corda.plugins.cli

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar


class CliPluginGeneratorPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {

        project.pluginManager.apply(JavaPlugin)
        def extension = project.extensions.create("cliPlugin", CliPluginPackagerExtension.class)

        project.tasks.named("CliPluginPackage", Jar) {

            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            archiveBaseName = "plugin-${pluginId}"
            manifest {

                attributes['Plugin-Class'] = extension.cliPluginClass
                attributes['Plugin-Id'] = extension.cliPluginId
                attributes['Plugin-Version'] = project.version
                attributes['Plugin-Provider'] = extension.cliPluginProvider
                attributes['Plugin-Description'] = extension.cliPluginDescription
            }

            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            from main.output

            ConfigurationContainer runtimeConfigContainer = project.getConfigurations()
            Configuration runtimeConfig = runtimeConfigContainer.getByName("runtimeClasspath")

            dependsOn runtimeConfig
            from {
                runtimeConfig.collect { project.zipTree(it) }
            } {
                exclude "META-INF/*.SF"
                exclude "META-INF/*.DSA"
                exclude "META-INF/*.RSA"
                exclude "module-info.class"
                exclude "META-INF/versions/*/module-info.class"
                exclude "org/slf4j/**"

                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }
}
