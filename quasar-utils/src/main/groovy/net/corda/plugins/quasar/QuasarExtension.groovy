package net.corda.plugins.quasar


import groovy.transform.PackageScope
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

import javax.inject.Inject

class JavacPlugin {

    /**
     * Activates the javac compiler plugin to detect missing @Suspendable annotations
     */
    Property<Boolean> enable

    ListProperty<String> suspendableAnnotationMarkers

    /**
     * Add the specified annotation classes to the list of annotations that makes methods suspendable
     * @param suspendable annotation class names
     * @return
     */
    def suspendableAnnotationMarkers(String...classNames) {
        suspendableAnnotationMarkers.addAll(classNames)
    }

    ListProperty<String> suspendableThrowableMarkers

    /**
     * Add the specified exception classes to the list of exceptions that makes methods suspendable
     * @param suspendable exception class names
     * @return
     */
    def suspendableThrowableMarkers(String...classNames) {
        suspendableThrowableMarkers.addAll(classNames)
    }

    @Inject
    JavacPlugin(ObjectFactory objects) {
        enable = objects.property(Boolean.class).convention(false)
        suspendableAnnotationMarkers = objects.listProperty(String)
        suspendableThrowableMarkers = objects.listProperty(String)
    }
}

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
     * Activates the javac compiler plugin to detect missing @Suspendable annotations
     */
    private final JavacPlugin javacPluginExtension

    JavacPlugin getJavacPluginExtension() {
        return javacPluginExtension
    }

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

    def javacPlugin(Action<JavacPlugin> action) {
        action.execute(javacPluginExtension)
    }

    @Inject
    QuasarExtension(
        ObjectFactory objects,
        String defaultGroup,
        String defaultVersion,
        String defaultClassifier,
        Iterable<? extends String> initialPackageExclusions,
        Iterable<? extends String> initialClassLoaderExclusions
    ) {
        group = objects.property(String).convention(defaultGroup)
        version = objects.property(String).convention(defaultVersion)
        classifier = objects.property(String).convention(defaultClassifier)
        dependency = group.flatMap { grp ->
            version.flatMap { ver ->
                classifier.map { cls ->
                    [ group: grp, name: 'quasar-core', version: ver, classifier: cls, ext: 'jar' ]
                }
            }
        }
        javacPluginExtension = objects.newInstance(JavacPlugin.class)

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
