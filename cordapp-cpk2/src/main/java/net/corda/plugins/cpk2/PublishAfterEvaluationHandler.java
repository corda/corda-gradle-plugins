package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;
import static net.corda.plugins.cpk2.CordappUtils.isNullOrEmpty;

final class PublishAfterEvaluationHandler implements Action<Gradle> {
    private final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    private final Logger logger;
    private ArtifactoryPublisher artifactoryPublisher;

    private void enableArtifactoryPublisher(@NotNull Plugin<?> plugin) {
        ArtifactoryPublisher publisher;
        try {
            publisher = new ArtifactoryPublisher(plugin, logger);
        } catch (Exception e) {
            logger.warn("Cannot publish CPK companion POM to Artifactory");
            publisher = null;
        }
        artifactoryPublisher = publisher;
    }

    PublishAfterEvaluationHandler(@NotNull Project rootProject) {
        logger = rootProject.getLogger();
        rootProject.getPlugins().withId("com.jfrog.artifactory", this::enableArtifactoryPublisher);
    }

    @Override
    public void execute(@NotNull Gradle gradle) {
        for (Project project: gradle.getRootProject().getAllprojects()) {
            // The plugin ID is the only reliable way to check
            // whether a particular plugin has been applied.
            // Each sub-project can load its plugins into its
            // own classloader, which makes all the {@link Plugin}
            // implementation classes different.
            project.getPlugins().withId(CordappUtils.CORDAPP_CPK_PLUGIN_ID, plugin ->
                publishCompanionFor(project)
            );
        }
    }

    private void publishCompanionFor(@NotNull Project project) {
        final PublishingExtension extension = project.getExtensions().findByType(PublishingExtension.class);
        if (extension == null) {
            return;
        }

        final PublicationContainer publications = extension.getPublications();
        final PomXmlWriter pomXmlWriter = new PomXmlWriter(project.getConfigurations());
        publications.withType(MavenPublication.class)
            .matching(pub -> "jar".equals(pub.getPom().getPackaging()) && !isNullOrEmpty(pub.getGroupId()))
            .all(pub -> {
                // Create a "companion" POM to support transitive CPK relationships.
                final String publicationName = "cpk-" + pub.getName() + "-companion";
                final NamedDomainObjectProvider<MavenPublication> publicationProvider = publications.register(publicationName, MavenPublication.class, cpk -> {
                    cpk.setGroupId(CordappDependencyCollector.toCompanionGroupId(pub.getGroupId(), pub.getArtifactId()));
                    cpk.setArtifactId(CordappDependencyCollector.toCompanionArtifactId(pub.getArtifactId()));
                    cpk.setVersion(pub.getVersion());
                    maybeSetAlias(cpk, true);
                    cpk.pom(pom -> {
                        final MavenPom pubPom = pub.getPom();

                        pom.setPackaging("pom");
                        pom.getUrl().set(pubPom.getUrl());
                        pom.getName().set(pubPom.getName() + " Companion");
                        pom.getDescription().set(pubPom.getDescription());
                        pom.getInceptionYear().set(pubPom.getInceptionYear());
                        final MavenPomScm pubScm = maybeGetScm(pubPom);
                        if (pubScm != null) {
                            pom.scm(scm -> {
                                scm.getUrl().set(pubScm.getUrl());
                                scm.getTag().set(pubScm.getTag());
                                scm.getConnection().set(pubScm.getConnection());
                                scm.getDeveloperConnection().set(pubScm.getDeveloperConnection());
                            });
                        }
                        final MavenPomOrganization pubOrg = maybeGetOrganization(pubPom);
                        if (pubOrg != null) {
                            pom.organization(org -> {
                                org.getName().set(pubOrg.getName());
                                org.getUrl().set(pubOrg.getUrl());
                            });
                        }
                        final Iterable<MavenPomLicense> pubLicences = maybeGetLicences(pubPom);
                        if (pubLicences != null) {
                            pom.licenses(licences -> {
                                for (MavenPomLicense pubLicence: pubLicences) {
                                    licences.license(licence -> {
                                        licence.getUrl().set(pubLicence.getUrl());
                                        licence.getName().set(pubLicence.getName());
                                        licence.getComments().set(pubLicence.getComments());
                                        licence.getDistribution().set(pubLicence.getDistribution());
                                    });
                                }
                            });
                        }
                        final Iterable<MavenPomDeveloper> pubDevelopers = maybeGetDevelopers(pubPom);
                        if (pubDevelopers != null) {
                            pom.developers(developers -> {
                                for (MavenPomDeveloper pubDeveloper: pubDevelopers) {
                                    developers.developer(developer -> {
                                        developer.getId().set(pubDeveloper.getId());
                                        developer.getUrl().set(pubDeveloper.getUrl());
                                        developer.getName().set(pubDeveloper.getName());
                                        developer.getEmail().set(pubDeveloper.getEmail());
                                        developer.getRoles().set(pubDeveloper.getRoles());
                                        developer.getTimezone().set(pubDeveloper.getTimezone());
                                        developer.getProperties().set(pubDeveloper.getProperties());
                                        developer.getOrganization().set(pubDeveloper.getOrganization());
                                        developer.getOrganizationUrl().set(pubDeveloper.getOrganizationUrl());
                                    });
                                }
                            });
                        }
                        pom.withXml(pomXmlWriter);
                    });
                });
                if (artifactoryPublisher != null) {
                    artifactoryPublisher.publish(project.getTasks(), pub, publicationProvider);
                }
            });
    }

    /**
     * The {@link org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal#setAlias}
     * method belongs to Gradle's internal API, and so we cannot rely on it existing. We therefore try
     * to invoke it using reflection.
     */
    @SuppressWarnings("SameParameterValue")
    private void maybeSetAlias(MavenPublication publication, boolean alias) {
        try {
            lookup.findVirtual(publication.getClass(), "setAlias", methodType(void.class, boolean.class)).invoke(publication, alias);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            logger.warn("INTERNAL API: Cannot set alias for MavenPublication[" + publication.getName() + ']', t);
        }
    }

    /**
     * The {@link org.gradle.api.publish.maven.internal.publication.MavenPomInternal#getScm}
     * method belongs to Gradle's internal API, and so we cannot rely on it existing. We therefore
     * try to invoke it using reflection.
     */
    @Nullable
    private MavenPomScm maybeGetScm(@NotNull MavenPom pom) {
        try {
            final Object result = lookup.findVirtual(pom.getClass(), "getScm", methodType(MavenPomScm.class)).invoke(pom);
            return (result instanceof MavenPomScm) ? (MavenPomScm) result : null;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            logger.warn("INTERNAL API: Cannot read SCM from CPK POM", t);
            return null;
        }
    }

    /**
     * The {@link org.gradle.api.publish.maven.internal.publication.MavenPomInternal#getLicenses}
     * method belongs to Gradle's internal API, and so we cannot rely on it existing. We therefore
     * try to invoke it using reflection.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private List<MavenPomLicense> maybeGetLicences(@NotNull MavenPom pom) {
        try {
            final Object result = lookup.findVirtual(pom.getClass(), "getLicenses", methodType(List.class)).invoke(pom);
            return (result instanceof List) ? (List<MavenPomLicense>) result : null;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            logger.warn("INTERNAL API: Cannot read licenses from CPK POM", t);
            return null;
        }
    }

    /**
     * The {@link org.gradle.api.publish.maven.internal.publication.MavenPomInternal#getDevelopers}
     * method belongs to Gradle's internal API, and so we cannot rely on it existing. We therefore
     * try to invoke it using reflection.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private List<MavenPomDeveloper> maybeGetDevelopers(@NotNull MavenPom pom) {
        try {
            final Object result = lookup.findVirtual(pom.getClass(), "getDevelopers", methodType(List.class)).invoke(pom);
            return (result instanceof List) ? (List<MavenPomDeveloper>) result : null;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            logger.warn("INTERNAL API: Cannot read developers from CPK POM", t);
            return null;
        }
    }

    /**
     * The {@link org.gradle.api.publish.maven.internal.publication.MavenPomInternal#getOrganization}
     * method belongs to Gradle's internal API, and so we cannot rely on it existing. We therefore
     * try to invoke it using reflection.
     */
    @Nullable
    private MavenPomOrganization maybeGetOrganization(@NotNull MavenPom pom) {
        try {
            final Object result = lookup.findVirtual(pom.getClass(), "getOrganization", methodType(MavenPomOrganization.class)).invoke(pom);
            return (result instanceof MavenPomOrganization) ? (MavenPomOrganization) result : null;
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            logger.warn("INTERNAL API: Cannot read organization from CPK POM", t);
            return null;
        }
    }
}

/**
 * Integrate with the `com.jfrog.artifactory` Gradle plugin, which uses a
 * {@link org.gradle.api.ProjectEvaluationListener} to configure itself.
 * We can therefore assume here that any {@code ArtifactoryTask} objects
 * have already been configured.
 * <p>
 * All we need is the set of {@link MavenPublication} objects that are to be
 * published to Artifactory. If our CPK's own {@link MavenPublication} is
 * among them then we include its companion's publication too.
 */
final class ArtifactoryPublisher {
    private static final String ARTIFACTORY_TASK_NAME = "org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask";
    private static final String GET_PUBLICATIONS_METHOD_NAME = "getMavenPublications";

    private final Class<? extends Task> artifactoryTaskClass;
    private final Method mavenPublications;

    @SuppressWarnings("unchecked")
    ArtifactoryPublisher(@NotNull Plugin<?> plugin, @NotNull Logger logger) throws Exception {
        try {
            artifactoryTaskClass = (Class<? extends Task>) Class.forName(ARTIFACTORY_TASK_NAME, true, plugin.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            logger.warn("Task {} from Gradle com.jfrog.artifactory plugin is not available.", ARTIFACTORY_TASK_NAME);
            throw e;
        }

        try {
            mavenPublications = artifactoryTaskClass.getMethod(GET_PUBLICATIONS_METHOD_NAME);
        } catch (Exception e) {
            logger.warn("Cannot locate " + GET_PUBLICATIONS_METHOD_NAME + " method for ArtifactoryTask", e);
            throw e;
        }

        if (!Collection.class.isAssignableFrom(mavenPublications.getReturnType())) {
            logger.warn("Method {} does not return a collection type.", mavenPublications);
            throw new InvalidUserCodeException("Method " + mavenPublications + "has incompatible return type.");
        }
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private Collection<MavenPublication> getMavenPublications(@NotNull Task task) {
        try {
            return (Collection<MavenPublication>) mavenPublications.invoke(task);
        } catch (Exception e) {
            final Throwable ex = (e instanceof InvocationTargetException) ? ((InvocationTargetException) e).getTargetException() : e;
            throw new InvalidUserCodeException("Failed to extract Maven publications from " + task, ex);
        }
    }

    void publish(
        @NotNull TaskContainer tasks,
        @NotNull MavenPublication owner,
        @NotNull Provider<? extends MavenPublication> provider
    ) {
        tasks.withType(artifactoryTaskClass).configureEach(task -> {
            final Collection<MavenPublication> publications = getMavenPublications(task);
            if (publications.contains(owner)) {
                final MavenPublication publication = provider.get();
                if (publications.add(publication)) {
                    // Only modify the task graph if our publication wasn't already present.
                    task.dependsOn(tasks.named(getGeneratePomTaskName(publication), GenerateMavenPom.class));
                }
            }
        });
    }

    @NotNull
    private String getGeneratePomTaskName(@NotNull MavenPublication publication) {
        // This name is documented behaviour for Gradle's maven-publish plugin.
        return "generatePomFileFor" + capitalize(publication.getName()) + "Publication";
    }

    @NotNull
    private static String capitalize(@NotNull String str) {
        return str.isEmpty() ? str : Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
