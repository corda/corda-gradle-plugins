package net.corda.plugins.cli

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar


class CliPluginGeneratorPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {

        def pluginId = project.findProperty('pluginId')?.toString()?.trim()
        def pluginClass = project.findProperty('pluginClass')?.toString()?.trim()
        def pluginProvider = project.findProperty('pluginProvider')?.toString()?.trim()
        def pluginDescription = project.findProperty('pluginDescription')?.toString()?.trim()

        project.tasks.register("CliPluginPackage", Jar) {

            doLast {
                project.tasks.withType(Jar) {
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

                    archiveBaseName = "plugin-${pluginId}"
                    manifest {

                        attributes['Plugin-Class'] = pluginClass
                        attributes['Plugin-Id'] = pluginId
                        attributes['Plugin-Version'] = project.version
                        attributes['Plugin-Provider'] = pluginProvider
                        attributes['Plugin-Description'] = pluginDescription
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
    }
}
