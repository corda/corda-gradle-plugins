package net.corda.plugins.publish

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

import static org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME

@CompileStatic
@PackageScope
class MavenMapper {
    private final Map<ModuleVersionIdentifier, String> publishedAliases
    private final Set<ModuleVersionIdentifier> apiElements
    private final Set<ModuleVersionIdentifier> compileOnly
    private final Set<ModuleVersionIdentifier> compile
    private final Set<ModuleVersionIdentifier> runtime

    @PackageScope
    MavenMapper(ConfigurationContainer configurations, ResolvedConfiguration resolvedConfiguration) {
        // Ensure that we use these artifacts' published names, because
        // these aren't necessarily the same as their internal names.
        publishedAliases = resolvedConfiguration.resolvedArtifacts.collectEntries {
            [ (it.moduleVersion.id):it.name ]
        }.asUnmodifiable()

        // We cannot resolve the compileOnly and apiElements configurations
        // to determine all of the transitive dependencies they introduce.
        // Instead, we must derive these transitive dependencies from the
        // artifacts resolved from the compileClasspath configuration.
        TransitiveDependencyBuilder builder = new TransitiveDependencyBuilder(configurations, publishedAliases)
        ResolvedConfiguration compileClasspath = configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration

        apiElements = builder.createFor(API_ELEMENTS_CONFIGURATION_NAME).collectFrom(compileClasspath)
        compileOnly = builder.createFor(COMPILE_ONLY_CONFIGURATION_NAME).collectFrom(compileClasspath)
        compile = resolveArtifactsFor(configurations, COMPILE_CLASSPATH_CONFIGURATION_NAME)
        runtime = resolveArtifactsFor(configurations, RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    }

    @PackageScope
    String getScopeFor(ModuleVersionIdentifier id, String defaultScope) {
        if (id in compile && (!(id in compileOnly) || id in apiElements)) {
            // This dependency is on the compile classpath. Also, it has
            //    EITHER not been declared as "compileOnly"
            //    OR been declared as an "api" element (c.f. java-library plugin)
            return "compile"
        } else if (id in runtime) {
            // This dependency is on the runtime classpath and hasn't been
            // claimed by the "compile" scope.
            return "runtime"
        } else {
            // This dependency probably belongs to a configuration created by the user.
            return defaultScope
        }
    }

    @PackageScope
    String getModuleNameFor(ModuleVersionIdentifier id) {
        return publishedAliases[id]
    }

    private static Set<ModuleVersionIdentifier> resolveArtifactsFor(
        ConfigurationContainer configurations,
        String configName
    ) {
        Configuration configuration = configurations.findByName(configName)
        if (configuration) {
            return configuration.resolvedConfiguration.resolvedArtifacts.collect { it.moduleVersion.id } as Set
        } else {
            return Collections.emptySet()
        }
    }

    private static class TransitiveDependencyBuilder {
        private final ConfigurationContainer configurations
        private final Map<ModuleVersionIdentifier, String> publishedAliases

        @PackageScope
        TransitiveDependencyBuilder(
            ConfigurationContainer configurations,
            Map<ModuleVersionIdentifier, String> publishedAliases
        ) {
            this.configurations = configurations
            this.publishedAliases = publishedAliases
        }

        @PackageScope
        Collector createFor(String configName) {
            return new Collector(getDependenciesFor(configName))
        }

        private Set<ModuleVersionIdentifier> getDependenciesFor(String configName) {
            Configuration configuration = configurations.findByName(configName)
            if (configuration) {
                return configuration.allDependencies.findAll { Dependency dep ->
                    dep.version && dep.group
                }.collect { Dependency dep ->
                    ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(dep.group, dep.name, dep.version)
                    String alias = publishedAliases[id]
                    alias == null ? id : DefaultModuleVersionIdentifier.newId(id.group, alias, id.version)
                } as Set
            } else {
                return Collections.emptySet()
            }
        }

        private static class Collector {
            private final Set<ModuleVersionIdentifier> targets

            @PackageScope
            Collector(Set<ModuleVersionIdentifier> targets) {
                this.targets = targets
            }

            @PackageScope
            Set<ModuleVersionIdentifier> collectFrom(ResolvedConfiguration resolved) {
                Set<ModuleVersionIdentifier> result = new LinkedHashSet<>()
                collect(resolved.firstLevelModuleDependencies, result)
                return result.asUnmodifiable()
            }

            private void collect(Set<ResolvedDependency> dependencies, Set<ModuleVersionIdentifier> result) {
                for (ResolvedDependency dependency: dependencies) {
                    if (dependency.module.id in targets) {
                        result.addAll(dependency.allModuleArtifacts.collect { it.moduleVersion.id })
                    } else {
                        collect(dependency.children, result)
                    }
                }
            }
        }
    }
}
