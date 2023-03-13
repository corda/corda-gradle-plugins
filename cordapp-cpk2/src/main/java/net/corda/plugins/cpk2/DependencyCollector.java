package net.corda.plugins.cpk2;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;

final class DependencyCollector {
    private final Set<String> excludeNames;

    DependencyCollector(@NotNull String... excludeName) {
        excludeNames = new LinkedHashSet<>();
        Collections.addAll(excludeNames, excludeName);
    }

    @NotNull
    public Set<Dependency> collectFrom(@NotNull Configuration source) {
        final Set<Dependency> result = new LinkedHashSet<>();
        collectFrom(source, result);
        return unmodifiableSet(result);
    }

    private void collectFrom(@NotNull Configuration source, @NotNull Set<Dependency> result) {
        if (!excludeNames.add(source.getName())) {
            return;
        }
        result.addAll(source.getDependencies());
        for (Configuration parent: source.getExtendsFrom()) {
            collectFrom(parent, result);
        }
    }
}
