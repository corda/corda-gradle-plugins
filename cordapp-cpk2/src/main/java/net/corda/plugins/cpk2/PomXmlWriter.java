package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import java.util.function.Function;

import static net.corda.plugins.cpk2.CordappDependencyCollector.toMarkerArtifactId;
import static net.corda.plugins.cpk2.CordappDependencyCollector.toMarkerGroupId;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.appendElement;
import static net.corda.plugins.cpk2.CordappUtils.resolveFirstLevel;
import static org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME;

final class PomXmlWriter implements Action<XmlProvider> {
    private final ConfigurationContainer configurations;

    PomXmlWriter(@NotNull ConfigurationContainer configurations) {
        this.configurations = configurations;
    }

    @Override
    public void execute(@NotNull XmlProvider xml) {
        final ResolvedConfiguration compileClasspath = configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).getResolvedConfiguration();
        final Element dependencies = appendElement(xml.asElement(), "dependencies");

        final DependencyXmlWriter cordappWriter = new DependencyXmlWriter(dependencies, CordappCoordinate::new);
        final DependencySet cordappDependencies = configurations.getByName(CORDAPP_CONFIGURATION_NAME).getAllDependencies();
        resolveFirstLevel(compileClasspath, cordappDependencies).forEach(cordappWriter::write);

        final DependencyXmlWriter providedWriter = new DependencyXmlWriter(dependencies, ProvidedCoordinate::new);
        final DependencySet providedDependencies = configurations.getByName(CORDA_PROVIDED_CONFIGURATION_NAME).getAllDependencies();
        resolveFirstLevel(compileClasspath, providedDependencies).forEach(providedWriter::write);
    }
}

final class DependencyXmlWriter {
    private final Element dependencies;
    private final Function<ResolvedArtifact, ? extends MavenCoordinate> coordinateFactory;

    DependencyXmlWriter(@NotNull Element dependencies, @NotNull Function<ResolvedArtifact, ? extends MavenCoordinate> coordinateFactory) {
        this.dependencies = dependencies;
        this.coordinateFactory = coordinateFactory;
    }

    void write(@NotNull ResolvedArtifact artifact) {
        final MavenCoordinate maven = coordinateFactory.apply(artifact);
        final Element dependency = appendElement(dependencies, "dependency");
        appendElement(dependency, "groupId", maven.getGroupId());
        appendElement(dependency, "artifactId", maven.getArtifactId());
        appendElement(dependency, "version", maven.getVersion());
        final String classifier = maven.classifier;
        if (classifier != null) {
            appendElement(dependency, "classifier", classifier);
        }
        if (!"jar".equals(maven.type)) {
            appendElement(dependency, "type", maven.type);
        }
        appendElement(dependency, "scope", "compile");
    }
}

abstract class MavenCoordinate {
    protected final ModuleVersionIdentifier id;
    protected final String artifactName;
    protected final String classifier;
    protected final String type;

    MavenCoordinate(@NotNull ResolvedArtifact artifact) {
        id = artifact.getModuleVersion().getId();
        artifactName = artifact.getName();
        classifier = artifact.getClassifier();
        type = artifact.getType();
    }

    @Nullable abstract String getGroupId();
    @NotNull abstract String getArtifactId();

    @Nullable
    String getVersion() {
        return id.getVersion();
    }
}

final class CordappCoordinate extends MavenCoordinate {
    CordappCoordinate(@NotNull ResolvedArtifact artifact) {
        super(artifact);
    }

    @Override
    @NotNull
    String getGroupId() {
        return toMarkerGroupId(id.getGroup(), artifactName);
    }

    @Override
    @NotNull
    String getArtifactId() {
        return toMarkerArtifactId(artifactName);
    }
}

final class ProvidedCoordinate extends MavenCoordinate {
    ProvidedCoordinate(@NotNull ResolvedArtifact artifact) {
        super(artifact);
    }

    @Override
    @NotNull
    String getGroupId() {
        return id.getGroup();
    }

    @Override
    @NotNull
    String getArtifactId() {
        return artifactName;
    }
}
