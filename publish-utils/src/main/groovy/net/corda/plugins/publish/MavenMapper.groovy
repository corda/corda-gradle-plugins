package net.corda.plugins.publish

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class MavenMapper {
    private final Map<ModuleVersionIdentifier, String> publishedAliases
    private final Set<ModuleVersionIdentifier> apiElements
    private final Set<ModuleVersionIdentifier> compileOnly
    private final Set<ModuleVersionIdentifier> compile
    private final Set<ModuleVersionIdentifier> runtime

    MavenMapper(ConfigurationContainer configurations, ResolvedConfiguration resolvedConfiguration) {
        // Ensure that we use these artifacts' published names, because
        // these aren't necessarily the same as their internal names.
        publishedAliases = resolvedConfiguration.resolvedArtifacts.collectEntries {
            [ (it.moduleVersion.id):it.name ]
        }

        apiElements = getDependenciesFor(configurations, "apiElements", publishedAliases)
        compileOnly = resolveArtifactsFor(configurations, "compileOnly")
        compile = resolveArtifactsFor(configurations, "compileClasspath")
        runtime = resolveArtifactsFor(configurations, "runtimeClasspath")
    }

    String getScopeFor(ModuleVersionIdentifier id, String defaultScope) {
        if (compile.contains(id) && (!compileOnly.contains(id) || apiElements.contains(id))) {
            // This dependency is on the compile classpath. Also, it has
            //    EITHER not been declared as "compileOnly"
            //    OR been explicitly declared as an "api" element (c.f. java-library plugin)
            return "compile"
        } else if (runtime.contains(id)) {
            // This dependency is on the runtime classpath and hasn't been
            // claimed by the "compile" scope.
            return "runtime"
        } else {
            // This dependency probably belongs to a configuration created by the user.
            return defaultScope
        }
    }

    String getModuleNameFor(ModuleVersionIdentifier id) {
        return publishedAliases[id]
    }

    private static Set<ModuleVersionIdentifier> resolveArtifactsFor(
        ConfigurationContainer configurations,
        String configName
    ) {
        Configuration configuration = configurations.findByName(configName)
        if (configuration) {
            return configuration.resolvedConfiguration.resolvedArtifacts.collect { it.moduleVersion.id }.toSet()
        } else {
            return Collections.<ModuleVersionIdentifier>emptySet()
        }
    }

    private static Set<ModuleVersionIdentifier> getDependenciesFor(
        ConfigurationContainer configurations,
        String configName,
        Map<ModuleVersionIdentifier, String> publishedAliases
    ) {
        Configuration configuration = configurations.findByName(configName)
        if (configuration) {
            return configuration.allDependencies.iterator().findAll { Dependency dep ->
                dep.version && dep.group
            }.collect {
                Dependency dep = (Dependency) it
                ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(dep.group, dep.name, dep.version)
                String alias = publishedAliases[id]
                alias == null ? id : DefaultModuleVersionIdentifier.newId(id.group, alias, id.version)
            }.toSet()
        } else {
            return Collections.<ModuleVersionIdentifier>emptySet()
        }
    }
}
