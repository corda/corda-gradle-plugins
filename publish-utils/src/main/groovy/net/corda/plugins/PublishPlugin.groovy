package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.Project
import net.corda.plugins.bintray.*

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 * To apply this plugin you must also add 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.4' to your
 * buildscript's classpath dependencies.
 *
 * To use this plugin you can add a new configuration block (extension) to your root build.gradle. See the fields
 * in BintrayConfigExtension.
 */
class PublishPlugin implements Plugin<Project> {
    private ProjectPublishExtension publishConfig

    String getPublishName() { publishConfig.name }

    @Override
    void apply(Project project) {
        createExtensions(project)
        createConfigurations(project)
        createPlugins(project)
        createTasks(project)

        project.afterEvaluate {
            project.logger.info("Publishing ${project.name} as $publishName")
            new Publisher(project, publishConfig).execute()
        }
    }

    void createTasks(Project project) {
        if (project.hasProperty('classes')) {
            project.task("sourceJar", type: Jar, dependsOn: project.classes) {
                classifier = 'sources'
                from project.sourceSets.main.allSource
            }
        }

        if (project.hasProperty('javadoc')) {
            project.task("javadocJar", type: Jar, dependsOn: project.javadoc) {
                classifier = 'javadoc'
                from project.javadoc.destinationDir
            }
        }

        // Create the install task here so that modules can overwrite it (if required).
        project.task("install", dependsOn: "publishToMavenLocal")
    }

    void createExtensions(Project project) {
        publishConfig = project.extensions.create("publish", ProjectPublishExtension, project.name)

        if (project == project.rootProject) {
            project.extensions.create("bintrayConfig", BintrayConfigExtension)
        }
    }

    void createConfigurations(Project project) {
        project.configurations.create("publish")
    }

    void createPlugins(Project project) {
        project.apply([plugin: 'maven-publish'])
        project.apply([plugin: 'com.jfrog.bintray'])
    }
}
