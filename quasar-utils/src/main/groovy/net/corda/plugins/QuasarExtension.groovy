package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

import javax.inject.Inject

class QuasarExtension {

    final Property<Boolean> debug

    final Property<Boolean> verbose

    final ListProperty<String> excludePackages

    final Provider<String> options

    @Inject
    QuasarExtension(ObjectFactory objects, Iterable<? extends String> initialExclusions) {
        debug = objects.property(Boolean)
        debug.set(false)
        verbose = objects.property(Boolean)
        verbose.set(false)
        excludePackages = objects.listProperty(String)
        excludePackages.set(initialExclusions)
        options = excludePackages.map { excludes ->
            def builder = new StringBuilder('=')
            if (debug.get()) {
                builder.append('d')
            }
            if (verbose.get()) {
                builder.append('v')
            }
            if (!excludes.isEmpty()) {
                builder.append('x(').append(excludes.join(';')).append(')')
            }
            builder.length() == 1 ? '' : builder.toString()
        }
    }
}
