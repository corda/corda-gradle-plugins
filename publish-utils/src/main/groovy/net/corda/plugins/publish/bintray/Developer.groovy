package net.corda.plugins.publish.bintray

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

class Developer {
    private final Property<String> id
    private final Property<String> name
    private final Property<String> email

    @Inject
    Developer(ObjectFactory objects) {
        id = objects.property(String.class)
        name = objects.property(String.class)
        email = objects.property(String.class)
    }

    /**
     * A unique identifier the developer (eg; organisation ID)
     */
    Property<String> getId() {
        return id
    }

    /**
     * The full name of the developer
     */
    Property<String> getName() {
        return name
    }

    /**
     * An email address for contacting the developer
     */
    Property<String> getEmail() {
        return email
    }
}
