package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

import javax.inject.Inject

class QuasarExtension {

    final ListProperty<String> excludePackages

    final Provider<String> exclusions

    @Inject
    QuasarExtension(ObjectFactory objects) {
        excludePackages = objects.listProperty(String)
        exclusions = excludePackages.map { excludes ->
            excludes.isEmpty() ? '' : "=x(${excludes.join(';')})".toString()
        }
    }
}
