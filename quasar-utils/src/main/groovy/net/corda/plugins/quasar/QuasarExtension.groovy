package net.corda.plugins.quasar

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
     * Dependency notation for the Quasar bundle to use.
     */
    final Provider<Map<String, String>> dependency

    /**
     * Dependency notation for the Quasar agent to use.
     */
    final Provider<Map<String, String>> agent

    /**
     * Runtime options for the Quasar agent:
     * - debug
     * - verbose
     * - globs of packages not to instrument.
     * - globs of classloaders not to instrument.
     */
    final Property<Boolean> debug

    final Property<Boolean> verbose

    final ListProperty<String> excludePackages

    final ListProperty<String> excludeClassLoaders

    @PackageScope
    final Provider<String> options

    @Inject
    QuasarExtension(
        ObjectFactory objects,
        String defaultGroup,
        String defaultVersion,
        Iterable<? extends String> initialPackageExclusions,
        Iterable<? extends String> initialClassLoaderExclusions
    ) {
        group = objects.property(String).convention(defaultGroup)
        version = objects.property(String).convention(defaultVersion)
        dependency = group.zip(version) { grp, ver ->
            [ group: grp, name: 'quasar-core-osgi', version: ver, ext: 'jar' ]
        }
        agent = dependency.map {
            it['classifier'] = 'agent'
            it
        }

        debug = objects.property(Boolean).convention(false)
        verbose = objects.property(Boolean).convention(false)
        excludePackages = objects.listProperty(String)
        excludePackages.set(initialPackageExclusions)
        excludeClassLoaders = objects.listProperty(String)
        excludeClassLoaders.set(initialClassLoaderExclusions)
        options = excludePackages.flatMap { packages ->
            excludeClassLoaders.flatMap { classLoaders ->
                debug.flatMap { isDebug ->
                    verbose.map { isVerbose ->
                        def builder = new StringBuilder('=')
                        if (isDebug) {
                            builder.append('d')
                        }
                        if (isVerbose) {
                            builder.append('v')
                        }
                        if (!packages.isEmpty()) {
                            builder.append('x(').append(packages.join(';')).append(')')
                        }
                        if (!classLoaders.isEmpty()) {
                            builder.append('l(').append(classLoaders.join(';')).append(')')
                        }
                        builder.length() == 1 ? '' : builder.toString()
                    }
                }
            }
        }
    }
}
