package net.corda.plugins;

import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class FilteredPom extends DelegatedPom implements MavenPomInternal {
    public FilteredPom(@NotNull MavenPomInternal pom) {
        super(pom);
    }

    public Set<MavenDependency> getImportDependencyManagement() {
        return new HashSet<>();
    }
}
