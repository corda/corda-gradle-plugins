package net.corda.plugins.publish.bintray

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

@SuppressWarnings("unused")
class License {
    private final Property<String> name
    private final Property<String> url
    private final Property<String> distribution

    @Inject
    License(ObjectFactory objects) {
        name = objects.property(String.class)
        url = objects.property(String.class)
        distribution = objects.property(String.class)
    }

    /**
     * The name of license (eg; Apache 2.0)
     */
    Property<String> getName() {
        return name
    }

    /**
     * URL to the full license file
     */
    Property<String> getUrl() {
        return url
    }

    /**
     * The distribution level this license corresponds to (eg: repo)
     */
    Property<String> getDistribution() {
        return distribution
    }
}
