package net.corda.plugins

import org.gradle.api.artifacts.Configuration

class ProjectPublishExtension {
    private PublishTasks task

    void setPublishTask(PublishTasks task) {
        this.task = task
    }

    /**
     * Use a different name from the current project name for publishing.
     * Set this after all other settings that need to be configured
     */
    void name(String name) {
        task.setPublishName(name)
    }

    /**
     * Get the publishing name for this project.
     */
    String name() {
        return task.getPublishName()
    }

    /**
     * True when we do not want to publish default Java components
     */
    Boolean disableDefaultJar = false

    /**
     * True if publishing a WAR instead of a JAR. Forces disableDefaultJAR to "true" when true
     */
    Boolean publishWar = false

    /**
     * True if publishing sources to remote repositories
     */
    Boolean publishSources = true

    /**
     * True if publishing javadoc to remote repositories
     */
    Boolean publishJavadoc = true

    /**
     * The Gradle configuration that defines this artifact's dependencies.
     * This overrides the dependencies that would otherwise be derived
     * from "components.java" or "components.web".
     * Implies both "disableDefaultJar=true" and "publishWar=false"
     *
     * <pre>
     * {@code
     * publish {
     *     dependenciesFrom configurations.runtimeArtifacts
     * }
     * }
     * </pre>
     *
     * @param dependencies
     */
    void dependenciesFrom(Configuration dependencies) {
        task.publishDependencies = dependencies
    }
}