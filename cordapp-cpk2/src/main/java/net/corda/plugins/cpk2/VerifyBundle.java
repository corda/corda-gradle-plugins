package net.corda.plugins.cpk2;

import aQute.bnd.build.model.EE;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Clazz;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.osgi.Descriptors.TypeRef;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Packages;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.version.Version;
import aQute.bnd.version.VersionRange;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.inject.Inject;

import static aQute.bnd.osgi.Constants.EXPORT_PACKAGE;
import static aQute.bnd.osgi.Constants.OPTIONAL;
import static aQute.bnd.osgi.Constants.RESOLUTION_DIRECTIVE;
import static aQute.bnd.osgi.Constants.STRICT;
import static aQute.bnd.osgi.Constants.VERSION_ATTRIBUTE;
import static java.util.Collections.emptyMap;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.flatMapTo;
import static net.corda.plugins.cpk2.CordappUtils.joinToString;
import static net.corda.plugins.cpk2.CordappUtils.manifestOf;
import static net.corda.plugins.cpk2.CordappUtils.mapTo;
import static net.corda.plugins.cpk2.CordappUtils.map;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

@DisableCachingByDefault
public class VerifyBundle extends DefaultTask {
    private static final Pattern reservedPackageName = Pattern.compile("^net\\.corda(\\..+)?$");

    private final RegularFileProperty bundle;
    private final ConfigurableFileCollection _classpath;
    private final Property<Boolean> strict;

    @Inject
    public VerifyBundle(@NotNull ObjectFactory objects) {
        setDescription("Verifies that a bundle's OSGi meta-data is consistent.");
        setGroup(CORDAPP_TASK_GROUP);
        bundle = objects.fileProperty();
        _classpath = objects.fileCollection();
        strict = objects.property(Boolean.class).convention(true);
    }

    @PathSensitive(RELATIVE)
    @InputFile
    @NotNull
    public RegularFileProperty getBundle() {
        return bundle;
    }

    @PathSensitive(RELATIVE)
    @InputFiles
    @NotNull
    public FileCollection getClasspath() {
        return _classpath;
    }

    @Input
    @NotNull
    public Property<Boolean> getStrict() {
        return strict;
    }

    /**
     * Don't eagerly configure the {@link DependencyCalculator} task, even if
     * someone eagerly configures this {@link VerifyBundle} by accident.
     */
    void setDependenciesFrom(@NotNull TaskProvider<DependencyCalculator> task) {
        _classpath.setFrom(
            /*
             * These jars do not belong to this CPK, but provide
             * packages that the CPK will need at runtime.
             */
            task.flatMap(DependencyCalculator::getProvidedJars),
            task.flatMap(DependencyCalculator::getRemoteCordapps),
            task.flatMap(DependencyCalculator::getProjectCordapps),

            /*
             * These jars are the contents of this CPK's lib folder.
             */
            task.flatMap(DependencyCalculator::getLibraries)
        );
        _classpath.disallowChanges();
        dependsOn(task);
    }

    @TaskAction
    public void verify() {
        try (Jar jar = new Jar(bundle.get().getAsFile())) {
            verify(jar);
        } catch (IOException e) {
            throw new InvalidUserCodeException(e.getMessage(), e);
        }
    }

    private void verify(@NotNull Jar jar) {
        try (Verifier verifier = new Verifier(jar)) {
            verifier.setProperty(STRICT, strict.get().toString());
            verifier.verify();
            verifyImportPackage(verifier);
            verifyExportPackage(verifier);
            verifyPrivatePackage(verifier);

            final Logger logger = getLogger();
            final String jarName = jar.getSource().getName();
            for (String warning: verifier.getWarnings()) {
                logger.warn("{}: {}", jarName, warning);
            }

            final List<String> errors = verifier.getErrors();
            if (!errors.isEmpty()) {
                for (String error: errors) {
                    logger.error("{}: {}", jarName, error);
                }
                logger.error(
                    "Ensure that dependencies are OSGi bundles, and that they export every package {} needs to import.",
                    jarName);
                throw new InvalidUserCodeException("Bundle " + jarName + " has validation errors:"
                    + joinToString(errors, System.lineSeparator(), System.lineSeparator()));
            }
        } catch (Exception e) {
            throw new InvalidUserCodeException(e.getMessage(), e);
        }
    }

    @NotNull
    private List<String> filterReservedPackages(@NotNull Parameters parameters) {
        final List<String> result = new LinkedList<>();
        for (String key: parameters.keyList()) {
            if (reservedPackageName.matcher(key).matches()) {
                result.add(key);
            }
        }
        return result;
    }

    private void verifyExportPackage(@NotNull Verifier verifier) {
        map(filterReservedPackages(verifier.getExportPackage()),
            packageName -> "Export Package clause found for Corda package [" + packageName + ']'
        ).forEach(verifier::error);
    }

    private void verifyPrivatePackage(@NotNull Verifier verifier) {
        map(filterReservedPackages(verifier.getPrivatePackage()),
            packageName -> "Private package found for Corda package [" + packageName + ']'
        ).forEach(verifier::error);
    }

    private void verifyImportPackage(@NotNull Verifier verifier) {
        final Analyzer analyzer = (Analyzer) verifier.getParent();
        try {
            analyzer.analyze();
        } catch (Exception e) {
            throw new InvalidUserCodeException(e.getMessage(), e);
        }

        final Set<PackageRef> packageSpace = mapTo(new HashSet<>(), analyzer.getClassspace().keySet(), TypeRef::getPackageRef);
        final Clazz.JAVA highestEE = analyzer.getHighestEE();
        final Map<String, Attrs> systemPackages = highestEE != null ? EE.parse(highestEE.getEE()).getPackages() : emptyMap();
        final Set<String> classpathPackages = getClasspathPackages();
        Map<String, Set<Version>> exportVersions = new LinkedHashMap<>();
        fetchClasspathVersions(exportVersions);

        for (Map.Entry<PackageRef, Attrs> exportPackage: analyzer.getExports().entrySet()) {
            mapVersionsTo(exportPackage, exportVersions, PackageRef::getFQN);
        }
        final Packages imports = analyzer.getImports();
        imports.keySet().removeIf(packageRef -> systemPackages.containsKey(packageRef.getFQN()));
        imports.values().removeIf(value -> OPTIONAL.equals(value.get(RESOLUTION_DIRECTIVE)));
        for (Map.Entry<PackageRef, Attrs> importPackage: imports.entrySet()) {
            final PackageRef packageRef = importPackage.getKey();
            final String packageName = packageRef.getFQN();
            if (!packageSpace.contains(packageRef)
                    && !classpathPackages.contains(packageName)) {
                verifier.error("Import Package clause found for missing package [%s]", packageName);
            }

            final String importVersion = importPackage.getValue().get(VERSION_ATTRIBUTE);
            final Set<Version> exportVersion = exportVersions.get(packageName);
            if (importVersion != null
                    && (exportVersion == null || exportVersion.stream().noneMatch(new VersionRange(importVersion)::includes))) {
                verifier.error("Import Package clause requires package [%s] with version '%s', but version(s) '%s' exported",
                    packageName, importVersion, (exportVersion == null) ? "" : joinToString(exportVersion, ","));
            }
        }
    }

    @NotNull
    private Set<String> getClasspathPackages() {
        return flatMapTo(new HashSet<>(), getClasspath(), CordappUtils::getPackages);
    }

    private void fetchClasspathVersions(@NotNull Map<String, Set<Version>> exportVersions) {
        for (File file: getClasspath()) {
            final String exportPackage = manifestOf(file).getMainAttributes().getValue(EXPORT_PACKAGE);
            if (exportPackage != null) {
                for (Map.Entry<String, Attrs> header: OSGiHeader.parseHeader(exportPackage).entrySet()) {
                    mapVersionsTo(header, exportVersions, Function.identity());
                }
            }
        }
    }

    private <T> void mapVersionsTo(
        @NotNull Map.Entry<T, Attrs> header,
        @NotNull Map<String, Set<Version>> target,
        @NotNull Function<T, String> keyMapping
    ) {
        target.compute(keyMapping.apply(header.getKey()), (key, packageVersions) -> {
            final Set<Version> versions = packageVersions == null ? new LinkedHashSet<>() : packageVersions;
            final String packageVersion = header.getValue().get(VERSION_ATTRIBUTE);
            if (packageVersion != null) {
                versions.add(new Version(packageVersion));
            }
            return versions;
        });
    }
}
