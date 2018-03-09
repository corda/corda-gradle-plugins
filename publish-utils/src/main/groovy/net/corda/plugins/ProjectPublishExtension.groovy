package net.corda.plugins

class ProjectPublishExtension {

    ProjectPublishExtension(String name) {
        this.name = name
    }

    /**
     * This is the name that we will publish the module as.
     */
    String name

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
}