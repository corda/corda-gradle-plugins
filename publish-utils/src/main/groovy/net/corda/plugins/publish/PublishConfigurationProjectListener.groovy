package net.corda.plugins.publish

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import net.corda.plugins.publish.bintray.BintrayConfigExtension
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication

import static net.corda.plugins.publish.PublishPlugin.*

/**
 * This listener exists only because {@link com.jfrog.bintray.gradle.BintrayPlugin}
 * also uses a {@link ProjectEvaluationListener} to configure {@link BintrayUploadTask}.
 * Basically, we need to copy the contents of our own {@link BintrayConfigExtension}
 * into {@link BintrayExtension} before the Bintray plugin can configure all of its
 * {@link BintrayUploadTask}s.
 */
class PublishConfigurationProjectListener implements ProjectEvaluationListener {
    private final PublishExtension publishConfig
    private final Project project

    PublishConfigurationProjectListener(PublishExtension publishConfig, Project project) {
        this.publishConfig = publishConfig
        this.project = project
    }

    private boolean isListenerFor(Project project) {
        return this.project == project
    }

    private BintrayConfigExtension fetchBintrayConfig() {
        return project.rootProject.extensions.findByType(BintrayConfigExtension)
    }

    @Override
    void beforeEvaluate(Project project) {
    }

    @Override
    void afterEvaluate(final Project project, ProjectState state) {
        if (!isListenerFor(project)) {
            return
        }

        final BintrayConfigExtension bintrayConfig = fetchBintrayConfig()
        if (!bintrayConfig) {
            return
        }

        final String publishName = publishConfig.name.get()
        if (bintrayConfig.isPublishing(publishName)) {
            project.extensions.configure(BintrayExtension) { bintray ->
                project.logger.info('Configuring publishing for {}', publishName)
                bintray.user = bintrayConfig.user.getOrNull()
                bintray.key = bintrayConfig.key.getOrNull()
                bintray.publications = [ publishName ]
                bintray.dryRun = bintrayConfig.dryRun.get()
                bintray.pkg {
                    repo = bintrayConfig.repo.getOrNull()
                    name = publishName
                    userOrg = bintrayConfig.org.getOrNull()
                    licenses = bintrayConfig.licenses.getOrNull()

                    version {
                        gpg {
                            sign = bintrayConfig.gpgSign.get()
                            passphrase = bintrayConfig.gpgPassphrase.getOrNull()
                        }
                    }
                }
            }

            project.logger.info('Configuring maven publish for {}', publishName)
            PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
            publishing.publications.register(publishName, MavenPublication) { pub ->
                pub.groupId = project.group
                pub.artifactId = publishName

                if (publishConfig.publishSources.get()) {
                    Task sourceJar = project.tasks.findByName(SOURCES_TASK_NAME)
                    if (sourceJar) {
                        project.logger.info('Publishing sources for {}', publishName)
                        pub.artifact sourceJar
                    }
                }
                if (publishConfig.publishJavadoc.get()) {
                    Task javadocJar = project.tasks.findByName(JAVADOC_TASK_NAME)
                    if (javadocJar) {
                        project.logger.info('Publishing javadoc for {}', publishName)
                        pub.artifact javadocJar
                    }
                }

                project.configurations.getByName(PUBLISH_CONFIGURATION_NAME).artifacts.each {
                    project.logger.info('Adding artifact: {}', it.file.path)
                    delegate.artifact it
                }

                Configuration publishDependencies = publishConfig.publishDependencies
                if (publishDependencies) {
                    fromConfiguration(pub.pom, publishDependencies.resolvedConfiguration)
                } else if (!publishConfig.disableDefaultJar.get() && !publishConfig.publishWar.get()) {
                    from project.components.java
                } else if (publishConfig.publishWar.get()) {
                    from project.components.web
                }

                extendPomForMavenCentral(pub.pom, publishName, bintrayConfig)
            }
        } else {
            project.tasks.withType(BintrayUploadTask).configureEach { upload ->
                upload.enabled = false
            }
        }
    }

    private void fromConfiguration(MavenPom pom, ResolvedConfiguration configuration) {
        pom.withXml {
            Node dependenciesNode = asNode().appendNode('dependencies')
            MavenMapper mapper = new MavenMapper(project.configurations, configuration)

            configuration.firstLevelModuleDependencies.each {
                ModuleVersionIdentifier id = it.module.id

                Node dependencyNode = dependenciesNode.appendNode('dependency')
                dependencyNode.appendNode('groupId', it.moduleGroup)
                dependencyNode.appendNode('artifactId', mapper.getModuleNameFor(id))
                dependencyNode.appendNode('version', it.moduleVersion)

                /*
                 * Choose "compile" or "runtime" scope, depending on which Gradle
                 * configuration(s) this dependency belongs to. Or use the default
                 * scope if it can't be found at all.
                 */
                dependencyNode.appendNode('scope', mapper.getScopeFor(id, publishConfig.dependencyConfig.defaultScope.get()))
            }
        }
    }

    // Maven central requires all of the below fields for this to be a valid POM
    private void extendPomForMavenCentral(MavenPom pom, String publishName, BintrayConfigExtension config) {
        pom.withXml {
            asNode().children().last() + {
                resolveStrategy = DELEGATE_FIRST
                name publishName
                description project.description
                url config.projectUrl.get()
                scm {
                    url config.vcsUrl.get()
                }

                licenses {
                    license {
                        name config.license.name.get()
                        url config.license.url.get()
                        distribution config.license.url.get()
                    }
                }

                developers {
                    developer {
                        id config.developer.id.get()
                        name config.developer.name.get()
                        email config.developer.email.get()
                    }
                }
            }
        }
    }
}
