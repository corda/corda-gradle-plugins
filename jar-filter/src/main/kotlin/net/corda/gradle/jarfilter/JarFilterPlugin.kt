@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

/**
 * This plugin definition is only needed by the tests.
 */
class JarFilterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.info("Applying JarFilter plugin")
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw GradleException("The Jar-Filter plugin requires Gradle $MINIMUM_GRADLE_VERSION or newer.")
        }
    }
}
