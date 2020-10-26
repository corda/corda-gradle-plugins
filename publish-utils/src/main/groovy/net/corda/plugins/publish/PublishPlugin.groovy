package net.corda.plugins.publish

import com.jfrog.bintray.gradle.BintrayPlugin
import groovy.transform.PackageScope
import org.gradle.api.*
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GradleVersion

import static org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.JAVADOC_TASK_NAME
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import net.corda.plugins.publish.bintray.BintrayConfigExtension
import org.gradle.api.tasks.javadoc.Javadoc

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
    private static final String MINIMUM_GRADLE_VERSION = "5.1"

    @PackageScope
    static final String PUBLISH_CONFIGURATION_NAME = 'publish'

    @PackageScope
    static final String SOURCES_TASK_NAME = 'sourceJar'

    @PackageScope
    static final String JAVADOC_JAR_TASK_NAME = 'javadocJar'

    private PublishExtension publishConfig

    @Override
    void apply(Project project) {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw new GradleException("The Publish-Utils plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }

        publishConfig = project.extensions.create(PUBLISH_EXTENSION_NAME, PublishExtension, project.name)
        if (project == project.rootProject) {
            project.extensions.create(BINTRAY_CONFIG_EXTENSION_NAME, BintrayConfigExtension)
            createRootProjectListener(project)
        }

        // We cannot register new tasks inside the project's afterEvaluate handler,
        // which means we cannot risk registering plugins there either.
        project.pluginManager.apply(MavenPublishPlugin)
        project.pluginManager.apply(BintrayPlugin)
        project.pluginManager.apply(JavaPlugin)

        createTasks(project)
        createConfigurations(project)
    }

    private void createRootProjectListener(Project rootProject) {
        rootProject.gradle.addListener(new PublishConfigurationProjectListener(rootProject))
    }

    private void createTasks(Project project) {
        project.tasks.register(SOURCES_TASK_NAME, Jar) { task ->
            try {
                task.dependsOn(project.tasks.named(CLASSES_TASK_NAME))
                task.archiveClassifier.set('sources')
                task.from project.sourceSets.main.allSource
            } catch (UnknownTaskException ignored) {
                task.enabled = false
            }
        }

        project.tasks.register(JAVADOC_JAR_TASK_NAME, Jar) { task ->
            try {
                def javadoc = project.tasks.named(JAVADOC_TASK_NAME, Javadoc)
                task.dependsOn(javadoc)
                task.archiveClassifier.set('javadoc')
                task.from javadoc.map { it.destinationDir }
            } catch (UnknownTaskException ignored) {
                task.enabled = false
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
