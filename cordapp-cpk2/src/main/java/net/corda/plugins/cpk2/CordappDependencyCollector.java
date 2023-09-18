package net.corda.plugins.cpk2;

import static net.corda.plugins.cpk2.Attributor.isPlatformModule;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_EXTERNAL_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.SEPARATOR;
import static net.corda.plugins.cpk2.CordappUtils.filterIsInstance;
import static net.corda.plugins.cpk2.CordappUtils.joinToString;
import static net.corda.plugins.cpk2.CordappUtils.toMaven;
import static net.corda.plugins.cpk2.Transitive.TRANSITIVE_ATTRIBUTE;
import static java.util.Collections.unmodifiableSet;

final class CordappDependencyCollector {
    private static final String CPK_SUFFIX = ".corda.cpk";
    private static final String DEPENDENCY_GROUP = "group";
    private static final String DEPENDENCY_NAME = "name";
    private static final String DEPENDENCY_VERSION = "version";

    private final ConfigurationContainer configurations;
    private final DependencyHandler dependencyHandler;
    private final Attributor attributor;
    private final Logger logger;

    private final Set<ModuleDependency> platforms;
    private final Set<ProjectDependency> cordappProjects;
    private final Set<Dependency> cordappModules;
    private final Set<Dependency> provided;

    CordappDependencyCollector(
        @NotNull ConfigurationContainer configurations,
        @NotNull DependencyHandler dependencyHandler,
        @NotNull Attributor attributor,
        @NotNull Logger logger
    ) {
        this.configurations = configurations;
        this.dependencyHandler = dependencyHandler;
        this.attributor = attributor;
        this.logger = logger;

        platforms = new LinkedHashSet<>();
        cordappProjects = new LinkedHashSet<>();
        cordappModules = new LinkedHashSet<>();
        provided = new LinkedHashSet<>();
    }

    @NotNull
    synchronized Set<Dependency> getCordappDependencies() {
        final Set<Dependency> result = new LinkedHashSet<>(cordappModules);
        result.addAll(cordappProjects);
        return unmodifiableSet(result);
    }

    @NotNull
    synchronized Set<Dependency> getProvidedDependencies() {
        return unmodifiableSet(provided);
    }

    synchronized void collect() {
        // Identify any Gradle platform dependencies we may have.
        // We will need these when we resolve our detached configurations.
        // IMPORTANT! We MUST NOT resolve the "cordappExternal" configuration here!
        filterIsInstance(
            configurations.getByName(CORDAPP_EXTERNAL_CONFIGURATION_NAME).getAllDependencies(),
            ModuleDependency.class
        ).forEach(moduleDependency -> {
            if (isPlatformModule(moduleDependency)) {
                platforms.add(moduleDependency);
            }
        });

        // Now walk through our "cordapp" dependencies.
        collectFrom(configurations.getByName(CORDAPP_CONFIGURATION_NAME).getAllDependencies());
    }

    @NotNull
    private ResolvedConfiguration resolve(@NotNull Dependency dependency) {
        // Configuration.extendsFrom() appears broken for detached configurations.
        // See https://github.com/gradle/gradle/issues/6881.
        final Dependency[] dependencies = new Dependency[1 + platforms.size()];
        int idx = 0;
        dependencies[idx] = dependency;
        for (ModuleDependency platform: platforms) {
            dependencies[++idx] = platform;
        }

        final Configuration detached = configurations.detachedConfiguration(dependencies)
            .attributes(attributor::forCompileClasspath)
            .setVisible(false);
        detached.setCanBeConsumed(false);
        return detached.getResolvedConfiguration();
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private boolean hasCollectables(@NotNull ModuleDependency dependency) {
        /*
         * We are synchronising with the {@link Configuration#withDependencies}
         * handler for the {@code allCordapps} configuration.
         * @see CordappPlugin#apply.
         */
        final boolean isTransitive;
        synchronized(dependency) {
            isTransitive = dependency.isTransitive() || dependency.getAttributes().contains(TRANSITIVE_ATTRIBUTE);
        }
        return isTransitive && !isPlatformModule(dependency);
    }

    private void collectFrom(@NotNull DependencySet dependencies) {
        for (Dependency dependency: dependencies) {
            if (dependency instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) dependency;
                if (cordappProjects.add(projectDependency) && hasCollectables(projectDependency)) {
                    // Resolve a CorDapp dependency from another
                    // module in a multi-module build.
                    collectFrom(projectDependency);
                }
            } else if (dependency instanceof ModuleDependency) {
                final ModuleDependency moduleDependency = (ModuleDependency) dependency;
                if (cordappModules.add(moduleDependency) && hasCollectables(moduleDependency)) {
                    final Map<String, String> depMap = asMap(moduleDependency);
                    if (moduleDependency.getVersion() == null && !platforms.isEmpty()) {
                        // This dependency has no explicit version of its own.
                        // Try to learn which version to use by resolving it
                        // against our platform dependencies.
                        final Set<ResolvedDependency> resolved = resolve(moduleDependency).getFirstLevelModuleDependencies(dep ->
                            Objects.equals(dep.getGroup(), moduleDependency.getGroup())
                                && Objects.equals(dep.getName(), moduleDependency.getName())
                        );
                        if (resolved.size() == 1) {
                            final ResolvedDependency dep = resolved.iterator().next();
                            depMap.put(DEPENDENCY_VERSION, dep.getModuleVersion());
                        }
                    }
                    final Dependency cordapp = dependencyHandler.create(toCPK(depMap));
                    // Try to resolve the CorDapp's "marker" dependency.
                    // This may not exist, although it's better if it does.
                    collectFrom(cordapp);
                }
            }
        }
    }

    private void collectFrom(@NotNull ProjectDependency cordapp) {
        final ConfigurationContainer cordappConfigurations = cordapp.getDependencyProject().getConfigurations();
        final Configuration cordappTransitives = cordappConfigurations.findByName(CORDAPP_CONFIGURATION_NAME);
        if (cordappTransitives != null) {
            collectFrom(cordappTransitives.getAllDependencies());
        }
        final Configuration providedTransitives = cordappConfigurations.findByName(CORDA_PROVIDED_CONFIGURATION_NAME);
        if (providedTransitives != null) {
            provided.addAll(providedTransitives.getAllDependencies());
        }
    }

    private void collectFrom(@NotNull Dependency cordapp) {
        final ResolvedConfiguration resolved = resolve(cordapp);
        if (resolved.hasError()) {
            logger.warn("CorDapp has unresolved dependencies:{}",
                joinToString(resolved.getLenientConfiguration().getUnresolvedModuleDependencies(), SEPARATOR, SEPARATOR));
            logger.warn("Cannot resolve CPK marker artifact '{}' - SKIPPED", toMaven(cordapp));
        } else {
            // This should never now throw ResolveException.
            collectFrom(resolved.getFirstLevelModuleDependencies(dep ->
                dep instanceof ModuleDependency && !isPlatformModule((ModuleDependency) dep)
            ), new LinkedHashSet<>());
        }
    }

    private void collectFrom(
        @NotNull Set<ResolvedDependency> resolvedDeps,
        @NotNull Set<ResolvedDependency> result
    ) {
        for (ResolvedDependency resolved: resolvedDeps) {
            if (result.add(resolved)) {
                final Map<String, String> dependency = asMap(resolved);
                if (isCPK(dependency)) {
                    cordappModules.add(dependencyHandler.create(toCordapp(dependency)));
                    collectFrom(resolved.getChildren(), result);
                } else {
                    provided.add(dependencyHandler.create(dependency));
                }
            }
        }
    }

    @NotNull
    static String toMarkerGroupId(@Nullable String group, @NotNull String artifactId) {
        return toCpkPrefix(group) + artifactId;
    }

    @NotNull
    static String toMarkerArtifactId(@NotNull String artifactId) {
        return artifactId + CPK_SUFFIX;
    }

    @NotNull
    private static Map<String, String> asMap(@NotNull ResolvedDependency dep) {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(DEPENDENCY_NAME, dep.getModuleName());
        result.put(DEPENDENCY_GROUP, dep.getModuleGroup());
        result.put(DEPENDENCY_VERSION, dep.getModuleVersion());
        return result;
    }

    @NotNull
    private static Map<String, String> asMap(@NotNull Dependency dep) {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(DEPENDENCY_NAME, dep.getName());
        final String group = dep.getGroup();
        if (group != null) {
            result.put(DEPENDENCY_GROUP, group);
        }
        final String version = dep.getVersion();
        if (version != null) {
            result.put(DEPENDENCY_VERSION, version);
        }
        return result;
    }

    private static boolean isCPK(@NotNull Map<String, String> map) {
        final String group = map.get(DEPENDENCY_GROUP);
        if (group == null) {
            return false;
        }
        final String name = map.get(DEPENDENCY_NAME);
        return name.length() > CPK_SUFFIX.length()
            && name.endsWith(CPK_SUFFIX)
            && hasSuffix(group, dropLast(name, CPK_SUFFIX.length()));
    }

    /**
     * This should only be invoked if {@link #isCPK} has already returned `true`.
     */
    @NotNull
    private static Map<String, String> toCordapp(@NotNull Map<String, String> map) {
        final String cordappName = removeSuffix(map.get(DEPENDENCY_NAME), CPK_SUFFIX);
        final String cpkGroup = map.get(DEPENDENCY_GROUP);
        if (cpkGroup.length() == cordappName.length()) {
            map.remove(DEPENDENCY_GROUP);
        } else {
            map.put(DEPENDENCY_GROUP, dropLast(cpkGroup, cordappName.length() + 1));
        }
        map.put(DEPENDENCY_NAME, cordappName);
        return map;
    }

    @NotNull
    private static Map<String, String> toCPK(@NotNull Map<String, String> map) {
        final String artifactName = map.get(DEPENDENCY_NAME);
        map.put(DEPENDENCY_GROUP, toMarkerGroupId(map.get(DEPENDENCY_GROUP), artifactName));
        map.put(DEPENDENCY_NAME, toMarkerArtifactId(artifactName));
        return map;
    }

    @NotNull
    private static String toCpkPrefix(@Nullable String group) {
        return group == null ? "" : group + '.';
    }

    @NotNull
    private static String dropLast(@NotNull String str, int count) {
        final int newLength = str.length() - count;
        return newLength <= 0 ? "" : str.substring(0, newLength);
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    private static String removeSuffix(@NotNull String str, @NotNull String suffix) {
        return str.endsWith(suffix) ? str.substring(0, str.length() - suffix.length()) : str;
    }

    private static boolean hasSuffix(@NotNull String str, @NotNull String suffix) {
        if (str.length() == suffix.length()) {
            return str.equals(suffix);
        } else {
            final int idx = str.length() - suffix.length() - 1;
            return idx > 0 && str.endsWith(suffix) && str.charAt(idx) == '.';
        }
    }
}
