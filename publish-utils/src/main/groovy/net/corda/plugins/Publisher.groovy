package net.corda.plugins

import net.corda.plugins.bintray.BintrayConfigExtension
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication

class Publisher {
    private final Project project
    private final ProjectPublishExtension publishConfig

    String getPublishName() { publishConfig.name }

    Publisher(Project project, ProjectPublishExtension config) {
        this.project = project
        this.publishConfig = config
    }

    void execute() {
        project.logger.info("Checking whether to publish $publishName")
        def bintrayConfig = project.rootProject.extensions.findByType(BintrayConfigExtension.class)
        if ((bintrayConfig != null) && bintrayConfig.publications && (bintrayConfig.publications.findAll { it == publishName }.size() > 0)) {
            configurePublishing(bintrayConfig)
        }
    }

    private void configurePublishing(BintrayConfigExtension bintrayConfig) {
        project.logger.info("Configuring bintray for $publishName")
        configureMavenPublish(bintrayConfig)
        configureBintray(bintrayConfig)
    }

    private void configureMavenPublish(BintrayConfigExtension bintrayConfig) {
        project.logger.info("Configuring maven publish for $publishName")
        project.publishing.publications.create(publishName, MavenPublication) { maven ->
            maven.groupId project.group
            maven.artifactId publishName

            if (publishConfig.publishSources) {
                project.logger.info("Publishing sources for $publishName")
                maven.artifact project.tasks.sourceJar
            }
            if (publishConfig.publishJavadoc) {
                project.logger.info("Publishing javadoc for $publishName")
                maven.artifact project.tasks.javadocJar
            }

            project.configurations.publish.artifacts.each {
                project.logger.info("Adding artifact: ${it.file}")
                delegate.artifact it
            }

            if (!publishConfig.disableDefaultJar && !publishConfig.publishWar) {
                maven.from project.components.java
            } else if (publishConfig.publishWar) {
                maven.from project.components.web
            }

            extendPomForMavenCentral(pom, bintrayConfig)
        }
    }

    // Maven central requires all of the below fields for this to be a valid POM
    private void extendPomForMavenCentral(MavenPom pom, BintrayConfigExtension config) {
        pom.withXml {
            asNode().children().last() + {
                resolveStrategy = DELEGATE_FIRST
                name publishName
                description project.description
                url config.projectUrl
                scm {
                    url config.vcsUrl
                }

                licenses {
                    license {
                        name config.license.name
                        url config.license.url
                        distribution config.license.url
                    }
                }

                developers {
                    developer {
                        id config.developer.id
                        name config.developer.name
                        email config.developer.email
                    }
                }
            }
        }
    }

    private void configureBintray(BintrayConfigExtension bintrayConfig) {
        project.bintray {
            user = bintrayConfig.user
            key = bintrayConfig.key
            publications = [ publishName ]
            dryRun = bintrayConfig.dryRun ?: false
            pkg {
                repo = bintrayConfig.repo
                name = publishName
                userOrg = bintrayConfig.org
                licenses = bintrayConfig.licenses

                version {
                    gpg {
                        sign = bintrayConfig.gpgSign ?: false
                        passphrase = bintrayConfig.gpgPassphrase
                    }
                }
            }
        }
    }
}
