package net.corda.plugins.cpk2;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.inject.Inject;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static net.corda.plugins.cpk2.CordappUtils.ALL_CORDAPPS_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_DOCUMENTATION_URL;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_EXTERNAL_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_PACKAGING_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_ALL_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_API_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_EMBEDDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_PROVIDED_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_RUNTIME_ONLY_CONFIGURATION_NAME;
import static net.corda.plugins.cpk2.CordappUtils.ENTERPRISE_API_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.filterIsInstance;
import static net.corda.plugins.cpk2.CordappUtils.flatMapTo;
import static net.corda.plugins.cpk2.CordappUtils.mapTo;
import static net.corda.plugins.cpk2.CordappUtils.resolveAll;
import static net.corda.plugins.cpk2.CordappUtils.resolveFirstLevel;
import static net.corda.plugins.cpk2.CordappUtils.subtract;
import static org.gradle.api.specs.Specs.satisfyNone;

@DisableCachingByDefault
public class DependencyCalculator extends DefaultTask {
    private static final boolean NON_CORDA = false;
    private static final boolean CORDA = true;

    private static final String[][] HARDCODED_EXCLUDES = {
        { "org.jetbrains", "annotations" },
        { "org.jetbrains.kotlin", "*" },
        { "net.corda.kotlin", "*" },
        { "org.osgi", "*" },
        { "org.slf4j", "slf4j-api" },
        { "org.slf4j", "jcl-over-slf4j" },
        { "commons-logging", "commons-logging" },
        { "co.paralleluniverse", "quasar-core" },
        { "co.paralleluniverse", "quasar-core-osgi" }
    };
    private static final Logger log = LoggerFactory.getLogger(DependencyCalculator.class);

    /**
     * Gradle's configuration cache forbids invoking {@link org.gradle.api.Task#getProject}
     * during the task execution phase. Although to be blunt, Gradle cannot serialise
     * a {link ConfigurationContainer} field yet either!
     */
    private final ConfigurationContainer configurations;

    private final ConfigurableFileCollection _libraries;
    private final ConfigurableFileCollection _projectCordapps;
    private final ConfigurableFileCollection _remoteCordapps;
    private final ConfigurableFileCollection _providedJars;
    private final ConfigurableFileCollection _embeddedJars;
    private final ConfigurableFileCollection _unbundledJars;

    @Inject
    public DependencyCalculator(@NotNull ObjectFactory objects) {
        setDescription("Computes this CorDapp's dependencies.");
        setGroup(CORDAPP_TASK_GROUP);

        configurations = getProject().getConfigurations();
        _libraries = objects.fileCollection();
        _projectCordapps = objects.fileCollection();
        _remoteCordapps = objects.fileCollection();
        _providedJars = objects.fileCollection();
        _embeddedJars = objects.fileCollection();
        _unbundledJars = objects.fileCollection();

        // Force this task to execute!
        getOutputs().upToDateWhen(satisfyNone());
    }

    /**
     * @return Jars which are added to the CPK's lib/ folder.
     */
    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getLibraries() {
        return _libraries.getElements();
    }

    /**
     * @return The "main" jars from all of our dependent CorDapp CPKs,
     * including any transitive CPK dependencies.
     */
    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getProjectCordapps() {
        return _projectCordapps.getElements();
    }

    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getRemoteCordapps() {
        return _remoteCordapps.getElements();
    }

    /**
     * @return The resolved contents of the `cordaAllProvided` configuration,
     * which should contain all of the Corda API jars that this CorDapp uses.
     */
    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getProvidedJars() {
        return _providedJars.getElements();
    }

    /**
     * @return Jars are embedded into the "main" jar and
     * then added to its Bundle-Classpath. Used for jars
     * whose OSGi metadata is either broken or missing.
     */
    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getEmbeddedJars() {
        return _embeddedJars.getElements();
    }

    /**
     * @return Jars have been migrated off the Bundle-Classpath
     * and restored to Bnd's regular classpath. (We only use
     * this property internally.)
     */
    @OutputFiles
    @NotNull
    public Provider<Set<FileSystemLocation>> getUnbundledJars() {
        return _unbundledJars.getElements();
    }

    @TaskAction
    public void calculate() {
        // Compute the (unresolved) dependencies on the packaging classpath
        // that the user has selected for this CPK archive. We ignore any
        // dependencies from the cordaRuntimeOnly configuration because
        // these will be provided by Corda. Also ignore anything from the
        // cordaEmbedded configuration, because these will be added to the
        // bundle classpath.
        final Configuration runtimeConfiguration = configurations.getByName(CORDAPP_PACKAGING_CONFIGURATION_NAME);
        final Set<Dependency> runtimeDeps = new DependencyCollector(CORDA_RUNTIME_ONLY_CONFIGURATION_NAME, CORDA_EMBEDDED_CONFIGURATION_NAME)
            .collectFrom(runtimeConfiguration);

        // There are some dependencies that Corda MUST always provide,
        // even if the CorDapp also declares them itself.
        // Extract any of these from the user's runtime dependencies.
        final Set<Dependency> cordaDeps;
        final Set<Dependency> nonCordaDeps;
        {
            final Map<Boolean, Set<Dependency>> grouping = runtimeDeps.stream()
                .collect(groupingBy(dep -> isCordaProvided(dep.getGroup(), dep.getName()), toSet()));
            cordaDeps = grouping.getOrDefault(CORDA, emptySet());
            nonCordaDeps = grouping.getOrDefault(NON_CORDA, emptySet());
        }

        // Compute the set of resolved artifacts that will define this CorDapp.
        final ResolvedConfiguration packagingConfiguration = runtimeConfiguration.getResolvedConfiguration();
        final Set<File> packageFiles = toFiles(subtract(
            resolveWithoutCorda(packagingConfiguration, nonCordaDeps),
            resolveAll(packagingConfiguration, cordaDeps)
        ));
        packageFiles.addAll(toFileCollectionFiles(nonCordaDeps));

        // Separate out any jars which we want to embed instead.
        // Avoid embedding anything which another CorDapp depends on.
        final Set<Dependency> embeddedDeps = subtract(
            configurations.getByName(CORDA_EMBEDDED_CONFIGURATION_NAME).getAllDependencies(), runtimeDeps
        );
        final Set<File> embeddedFiles = toFiles(resolveWithoutCorda(packagingConfiguration, embeddedDeps));
        embeddedFiles.addAll(toFileCollectionFiles(embeddedDeps));

        // The user has explicitly asked to embed these artifacts.
        final Set<File> mustEmbedFiles = resolveFirstLevelFilesFor(packagingConfiguration, embeddedDeps);

        final Set<File> bundledFiles = subtract(embeddedFiles, packageFiles);
        bundledFiles.addAll(mustEmbedFiles);
        _embeddedJars.setFrom(bundledFiles);
        _embeddedJars.disallowChanges();

        _unbundledJars.setFrom(subtract(embeddedFiles, bundledFiles));
        _unbundledJars.disallowChanges();

        _libraries.setFrom(subtract(packageFiles, bundledFiles));
        _libraries.disallowChanges();
        for (File library: _libraries) {
            getLogger().info("CorDapp library dependency: {}", library.getName());
        }

        // Finally, work out which jars we have used that have not been packaged into our CPK.
        // We still ignore anything that was for "compile only", because we only want to validate packages
        // that will be available at runtime.
        final ResolvedConfiguration externalConfiguration = configurations.getByName(CORDAPP_EXTERNAL_CONFIGURATION_NAME)
                .getResolvedConfiguration();
        final DependencySet cordappDeps = configurations.getByName(ALL_CORDAPPS_CONFIGURATION_NAME).getAllDependencies();
        final Set<File> cordappFiles = resolveFirstLevelFilesFor(externalConfiguration, cordappDeps);
        final List<ProjectDependency> projectCordappDeps = filterIsInstance(cordappDeps, ProjectDependency.class);
        final Set<File> projectCordappFiles = toFiles(resolveFirstLevel(externalConfiguration, projectCordappDeps));
        Set<String> cordappFileNames = new HashSet<>();
        for(File file: cordappFiles) {
            cordappFileNames.add(file.getName());
        }
        Set<File> cpbs = getCpbs(externalConfiguration);
        Set<File> transitives = new HashSet<>();
        try {
            transitives = extractJarsFromCpb(cpbs, cordappFileNames);
        } catch (IOException e) {
            log.warn("Could not resolve transitive dependencies from CPBs: {}", e.getMessage());
        }
        cordappFiles.addAll(transitives);
        _projectCordapps.setFrom(projectCordappFiles);
        _projectCordapps.disallowChanges();

        _remoteCordapps.setFrom(subtract(cordappFiles, projectCordappFiles));
        _remoteCordapps.disallowChanges();

        final DependencySet providedDeps = configurations.getByName(CORDA_ALL_PROVIDED_CONFIGURATION_NAME).getAllDependencies();
        final Set<File> providedFiles = resolveAllFilesFor(externalConfiguration, providedDeps);
        _providedJars.setFrom(providedFiles);
        _providedJars.disallowChanges();
    }

    @NotNull
    private static Set<File> getCpbs(ResolvedConfiguration externalConfiguration) {
        Set<File> cpbs = new HashSet<>();
        for (File file: externalConfiguration.getFiles()) {
            if (!file.getAbsolutePath().contains("/.")) {
                File dir = file.getAbsoluteFile().getParentFile();
                for (File siblingFile: Objects.requireNonNull(dir.listFiles())) {
                    if (siblingFile.isFile() && siblingFile.getAbsolutePath().endsWith(".cpb")) {
                        cpbs.add(siblingFile);
                    }
                }
            }
        }
        return cpbs;
    }

    @NotNull
    private static Set<File> extractJarsFromCpb(Set<File> cpbs, Set<String> cordappFileNames) throws IOException {
        Path pathToDir = Files.createTempDirectory("jars");
        pathToDir.toFile().deleteOnExit();
        Set<File> transitives = new HashSet<>();
        for (File cpb: cpbs) {
            JarFile jarFile = new JarFile(cpb);
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (!cordappFileNames.contains(jarEntry.getName())) {
                    if (jarEntry.getName().endsWith(".jar")) {
                        transitives.add(extractJarEntry(jarFile, jarEntry, pathToDir));
                    }
                }
            }
        }
        return transitives;
    }

    private static File extractJarEntry(JarFile jarFile, JarEntry jarEntry, Path pathToDir) throws IOException {
        File file;
        Path path = Paths.get(pathToDir.toString(), jarEntry.getName());
        if (!Files.exists(pathToDir)) {
            Files.createDirectories(pathToDir);
        }
        if (!Files.exists(path)) {
            InputStream inputStream = jarFile.getInputStream(jarEntry);
            Files.copy(inputStream, path);
            path.toFile().deleteOnExit();
        }
        file = path.toFile();
        file.deleteOnExit();
        return file;
    }

    @NotNull
    private static Set<File> resolveAllFilesFor(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies
    ) {
        final Set<File> result = toFiles(resolveAll(resolved, dependencies));
        result.addAll(toFileCollectionFiles(dependencies));
        return result;
    }

    @NotNull
    private static Set<File> resolveFirstLevelFilesFor(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies
    ) {
        final Set<File> result = toFiles(resolveFirstLevel(resolved, dependencies));
        result.addAll(toFileCollectionFiles(dependencies));
        return result;
    }

    @NotNull
    private static Set<File> toFiles(@NotNull Collection<ResolvedArtifact> artifacts) {
        return mapTo(new LinkedHashSet<>(), artifacts, ResolvedArtifact::getFile);
    }

    @NotNull
    private static Set<File> toFileCollectionFiles(@NotNull Collection<? extends Dependency> dependencies) {
        final List<FileCollectionDependency> fileDependencies = filterIsInstance(dependencies, FileCollectionDependency.class);
        return flatMapTo(new LinkedHashSet<>(), fileDependencies, FileCollectionDependency::resolve);
    }

    @NotNull
    private Set<ResolvedArtifact> resolveWithoutCorda(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies
    ) {
        final Set<ResolvedArtifact> artifacts = resolveAll(resolved, dependencies);
        artifacts.removeIf(artifact -> isCordaProvided(artifact.getModuleVersion().getId()));

        // Corda artifacts should not be included, either directly or transitively.
        warnAboutCordaArtifacts(CORDA_API_GROUP, artifacts);
        warnAboutCordaArtifacts(ENTERPRISE_API_GROUP, artifacts);
        return artifacts;
    }

    private void warnAboutCordaArtifacts(@NotNull String packageName, @NotNull Iterable<ResolvedArtifact> artifacts) {
        final int nameLength = packageName.length();
        for (ResolvedArtifact artifact: artifacts) {
            final String group = artifact.getModuleVersion().getId().getGroup();
            if (group.startsWith(packageName)) {
                if (nameLength == group.length()) {
                    throw new InvalidUserDataException("CorDapp must not contain '"
                        + artifact.getModuleVersion()
                        + "' (" + artifact.getFile().getName()
                    );
                } else if (group.length() > nameLength && group.charAt(nameLength) == '.') {
                    getLogger().warn("You appear to have included a Corda platform component '{}' ({}). "
                        + "You probably want to use either the {} or the {} configuration here. See {}",
                        artifact.getModuleVersion(),
                        artifact.getFile().getName(),
                        CORDA_PROVIDED_CONFIGURATION_NAME,
                        CORDAPP_CONFIGURATION_NAME,
                        CORDAPP_DOCUMENTATION_URL
                    );
                }
            }
        }
    }

    private static boolean isCordaProvided(@NotNull ModuleVersionIdentifier id) {
        return isCordaProvided(id.getGroup(), id.getName());
    }

    private static boolean isCordaProvided(@Nullable String group, @Nullable String name) {
        return Arrays.stream(HARDCODED_EXCLUDES).anyMatch(exclude ->
            (exclude[0].equals(group)) && (exclude[1].equals("*") || exclude[1].equals(name))
        );
    }
}
