package net.corda.plugins.publish

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

class MavenDependencyExtension {
    private final Property<String> defaultScope

    @Inject
    MavenDependencyExtension(ObjectFactory objects) {
        defaultScope = objects.property(String.class).convention("runtime")
    }

    Property<String> getDefaultScope() {
        return defaultScope
    }
}
