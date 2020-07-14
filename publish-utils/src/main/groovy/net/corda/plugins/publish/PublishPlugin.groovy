package net.corda.plugins.publish

import com.jfrog.bintray.gradle.BintrayPlugin
import groovy.transform.PackageScope
import org.gradle.api.*
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import net.corda.plugins.publish.bintray.BintrayConfigExtension

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 *
 * To use this plugin you can add a new configuration block (extension) to your root build.gradle.
 * See the fields in {@link BintrayConfigExtension}.
 */
@SuppressWarnings(['unused', 'GrMethodMayBeStatic'])
class PublishPlugin implements Plugin<Project> {
    private static final String BINTRAY_CONFIG_EXTENSION_NAME = 'bintrayConfig'
    private static final String PUBLISH_EXTENSION_NAME = 'publish'
    private static final String INSTALL_TASK_NAME = 'install'

    @PackageScope
    static final String PUBLISH_CONFIGURATION_NAME = 'publish'

    @PackageScope
    static final String SOURCES_TASK_NAME = 'sourceJar'

    @PackageScope
    static final String JAVADOC_TASK_NAME = 'javadocJar'

    private PublishExtension publishConfig

    @Override
    void apply(Project project) {
        publishConfig = project.extensions.create(PUBLISH_EXTENSION_NAME, PublishExtension, project.name)
        if (project == project.rootProject) {
            project.extensions.create(BINTRAY_CONFIG_EXTENSION_NAME, BintrayConfigExtension)
            createRootProjectListener(project)
        }

        // We cannot register new tasks inside the project's afterEvaluate handler,
        // which means we cannot risk registering plugins there either.
        project.pluginManager.apply(MavenPublishPlugin)
        project.pluginManager.apply(BintrayPlugin)

        createTasks(project)
        createConfigurations(project)
    }

    private void createRootProjectListener(Project rootProject) {
        rootProject.gradle.addListener(new PublishConfigurationProjectListener(rootProject))
    }

    private void createTasks(Project project) {
        if (project.hasProperty('classes')) {
            project.tasks.register(SOURCES_TASK_NAME, Jar) { task ->
                task.dependsOn(project.classes)
                task.archiveClassifier.set('sources')
                task.from project.sourceSets.main.allSource
            }
        }

        if (project.hasProperty('javadoc')) {
            project.tasks.register(JAVADOC_TASK_NAME, Jar) { task ->
                task.dependsOn(project.javadoc)
                task.archiveClassifier.set('javadoc')
                task.from project.javadoc.destinationDir
            }
        }

        // Register an "install" task, for those familiar with Maven.
        project.tasks.register(INSTALL_TASK_NAME) { Task task ->
            task.dependsOn(project.tasks.named('publishToMavenLocal'))
        }
    }

    private void createConfigurations(Project project) {
        project.configurations.create(PUBLISH_CONFIGURATION_NAME)
    }
}
