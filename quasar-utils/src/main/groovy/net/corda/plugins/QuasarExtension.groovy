package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

import javax.inject.Inject

class QuasarExtension {

    final ListProperty<String> excludePackages

    final Provider<String> exclusions

    @Inject
    QuasarExtension(ObjectFactory objects, Iterable<? extends String> initialExclusions) {
        excludePackages = objects.listProperty(String)
        excludePackages.set(initialExclusions)
        exclusions = excludePackages.map { excludes ->
            excludes.isEmpty() ? '' : "=x(${excludes.join(';')})".toString()
        }
    }
}
