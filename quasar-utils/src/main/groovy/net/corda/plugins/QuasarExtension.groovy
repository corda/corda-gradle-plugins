package net.corda.plugins

import groovy.transform.PackageScope
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

import javax.inject.Inject

class QuasarExtension {

    /**
     * Maven group for the Quasar agent.
     */
    final Property<String> group

    /**
     * Maven version for the Quasar agent.
     */
    final Property<String> version

    /**
     * Maven classifier for the Quasar agent.
     */
    final Property<String> classifier

    /**
     * Dependency notation for the Quasar agent to use.
     */
    final Provider<Map<String, String>> dependency

    /**
     * Runtime options for the Quasar agent:
     * - debug
     * - verbose
     * - globs of packages not to instrument.
     */
    final Property<Boolean> debug

    final Property<Boolean> verbose

    final ListProperty<String> excludePackages

    @PackageScope
    final Provider<String> options

    @Inject
    QuasarExtension(
        ObjectFactory objects,
        String defaultGroup,
        String defaultVersion,
        String defaultClassifier,
        Iterable<? extends String> initialExclusions
    ) {
        group = objects.property(String)
        group.set(defaultGroup)
        version = objects.property(String)
        version.set(defaultVersion)
        classifier = objects.property(String)
        classifier.set(defaultClassifier)
        dependency = group.map { grp ->
            [ group: grp, name: 'quasar-core', version: version.get(), classifier: classifier.get(), ext: 'jar' ]
        }

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
