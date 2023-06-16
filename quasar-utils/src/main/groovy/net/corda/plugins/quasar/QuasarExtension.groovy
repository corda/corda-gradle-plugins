package net.corda.plugins.quasar

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

import javax.inject.Inject

@CompileStatic
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
     * Whether we should apply the Quasar agent to Test tasks.
     */
    final Property<Boolean> instrumentTests

    /**
     * Whether we should apply the Quasar agent to JavaExec tasks.
     */
    final Property<Boolean> instrumentJavaExec

    /**
     * Dependency notation for the Quasar agent to use.
     */
    final Provider<Map<String, String>> dependency

    /**
     * Runtime options for the Quasar agent:
     * - debug
     * - verbose
     * - globs of packages not to instrument.
     * - globs of classloaders not to instrument.
     */
    final Property<Boolean> debug

    final Property<Boolean> verbose

    final Property<String> options

    final ListProperty<String> excludePackages

    final ListProperty<String> excludeClassLoaders

    @PackageScope
    final Provider<String> excludeOptions

    @Inject
    QuasarExtension(
        ObjectFactory objects,
        String defaultGroup,
        String defaultVersion,
        String defaultClassifier,
        Iterable<String> initialPackageExclusions,
        Iterable<String> initialClassLoaderExclusions
    ) {
        group = objects.property(String).convention(defaultGroup)
        version = objects.property(String).convention(defaultVersion)
        classifier = objects.property(String).convention(defaultClassifier)
        dependency = group.flatMap { grp ->
            version.flatMap { ver ->
                classifier.map { cls ->
                    [ group: grp, name: 'quasar-core', version: ver, classifier: cls, ext: 'jar' ] as Map<String, String>
                }
            }
        }

        instrumentTests = objects.property(Boolean).convention(true)
        instrumentJavaExec = objects.property(Boolean).convention(true)

        debug = objects.property(Boolean).convention(false)
        verbose = objects.property(Boolean).convention(false)
        options = objects.property(String).convention("")
        excludePackages = objects.listProperty(String)
        excludePackages.set(initialPackageExclusions)
        excludeClassLoaders = objects.listProperty(String)
        excludeClassLoaders.set(initialClassLoaderExclusions)
        excludeOptions = excludePackages.flatMap { List<String> packages ->
            excludeClassLoaders.flatMap { List<String> classLoaders ->
                debug.flatMap { isDebug ->
                    verbose.map { isVerbose ->
                        final def builder = new StringBuilder('')
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
