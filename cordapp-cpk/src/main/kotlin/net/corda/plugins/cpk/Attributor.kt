package net.corda.plugins.cpk

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory

/**
 * Generator for Gradle [Configuration][org.gradle.api.artifacts.Configuration]
 * variant attributes.
 */
internal class Attributor(private val objects: ObjectFactory) {
    /**
     * Dark Gradle Magic which ensures that a configuration
     * is resolved exactly like compileClasspath.
     */
    fun forCompileClasspath(attrs: AttributeContainer) {
        AttributeFactory(attrs, objects)
            .withExternalDependencies()
            .asLibrary()
            .javaApi()
            .jar()
    }

    /**
     * Dark Gradle Magic which ensures that a configuration
     * is resolved exactly like runtimeClasspath.
     */
    fun forRuntimeClasspath(attrs: AttributeContainer) {
        AttributeFactory(attrs, objects)
            .withExternalDependencies()
            .javaRuntime()
            .asLibrary()
            .jar()
    }

    /**
     * Dark Gradle Magic which ensures that we use a
     * project's jar artifact and not just its classes.
     */
    fun forJar(attrs: AttributeContainer) {
        AttributeFactory(attrs, objects).jar()
    }

    /**
     * Dark Gradle Magic to declare that we
     * consume or produce a CPK artifact.
     */
    fun forCpk(attrs: AttributeContainer) {
        AttributeFactory(attrs, objects).cpk()
    }
}
