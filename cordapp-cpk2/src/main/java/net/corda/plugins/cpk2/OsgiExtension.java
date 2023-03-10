package net.corda.plugins.cpk2;

import aQute.bnd.header.Attrs;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static aQute.bnd.header.OSGiHeader.parseHeader;
import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_CONFIG_PLUGIN_ID;
import static net.corda.plugins.cpk2.CordappUtils.IMPORT_POLICY_PACKAGES;
import static net.corda.plugins.cpk2.CordappUtils.REQUIRED_PACKAGES;
import static net.corda.plugins.cpk2.CordappUtils.isJavaIdentifiers;
import static net.corda.plugins.cpk2.CordappUtils.map;
import static org.osgi.framework.Constants.BUNDLE_CLASSPATH;
import static org.osgi.framework.Constants.IMPORT_PACKAGE;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.unmodifiableSet;

@SuppressWarnings("unused")
public class OsgiExtension {
    private static final String CORDAPP_CONFIG_FILENAME = "cordapp-configuration.properties";
    private static final String VERSION_RANGE_ATTRIBUTE = "range";
    private static final String DEFAULT_IMPORT_POLICY = "[=,+)";
    private static final String META_INF = "META-INF";
    private static final String OSGI_INF = "OSGI-INF";

    private static final Pattern CORDA_CLASSES = Pattern.compile("^Corda-.+-Classes$");

    /**
     * We need to import these packages so that the OSGi framework
     * will create bundle wirings for them. This allows Hibernate
     * to create lazy proxies for any JPA entities inside the CPK.
     * <p>
     * We DO NOT want to bind the CPK to use specific versions of
     * either Hibernate or Javassist here.
     */
    private static final Set<String> BASE_REQUIRED_PACKAGES = unmodifiableSet(new HashSet<>(asList(
        "org.hibernate.annotations",
        "org.hibernate.proxy"
    )));

    @NotNull
    private static int[] getPackageRange(@NotNull String[] packages) {
        int firstIdx = (packages.length > 2 && META_INF.equals(packages[0]) && "versions".equals(packages[1])) ? 3 : 0;
        // Range is first <= x < last
        return new int[]{ firstIdx, packages.length - 1 };
    }

    @NotNull
    private static Map<String, String> loadConfig(@NotNull URL resource) {
        final Properties props = new Properties();
        try (InputStream input = new BufferedInputStream(resource.openStream())) {
            props.load(input);
        } catch (IOException e) {
            throw new GradleException(e.getMessage(), e);
        }
        final Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry: props.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString().trim());
        }
        return result;
    }

    @NotNull
    private static String dashConcat(@NotNull String first, @Nullable String second) {
        return second == null || second.isEmpty() ? first : first + '-' + second;
    }

    @NotNull
    private static Set<String> parsePackages(@NotNull String value) {
        final Set<String> result = new LinkedHashSet<>();
        for (String element : value.split(",")) {
            result.add(element.trim());
        }
        return result;
    }

    @NotNull
    private static String consumerPolicy(@NotNull String value, @NotNull String versionPolicy) {
        return value + ":o;version='${range;" + versionPolicy + ";${@}}'";
    }

    @NotNull
    private static String dynamic(@NotNull String value) {
        return value + ";resolution:=dynamic;version=!";
    }

    @NotNull
    private static String optional(@NotNull String value) {
        return value + ";resolution:=optional";
    }

    @NotNull
    private static String emptyVersion(@NotNull String value) {
        return value + ";version='[0,0)'";
    }

    private final SetProperty<String> _noPackages;
    private final MapProperty<String, String> _noPolicies;
    private final MapProperty<String, String> _packagePolicies;
    private final SetProperty<String> _requiredPackages;
    private final MapProperty<String, String> _cordaClasses;
    private final SetProperty<String> _autoExportPackages;
    private final SetProperty<String> _exports;
    private final SetProperty<String> _imports;
    private final SetProperty<FileSystemLocation> _embeddeds;
    private final Property<Boolean> autoExport;
    private final Property<Boolean> applyImportPolicies;
    private final Property<String> scanCordaClasses;
    private final Property<String> symbolicName;
    private boolean configured;

    public OsgiExtension(@NotNull ObjectFactory objects, @NotNull Jar jar) {
        _noPackages = objects.setProperty(String.class);
        _noPackages.disallowChanges();
        _noPolicies = objects.mapProperty(String.class, String.class);
        _noPolicies.disallowChanges();
        _packagePolicies = objects.mapProperty(String.class, String.class);
        _requiredPackages = objects.setProperty(String.class).value(BASE_REQUIRED_PACKAGES);
        _cordaClasses = objects.mapProperty(String.class, String.class);
        _autoExportPackages = objects.setProperty(String.class);
        _exports = objects.setProperty(String.class);
        _imports = objects.setProperty(String.class);
        _imports.finalizeValueOnRead();
        _embeddeds = objects.setProperty(FileSystemLocation.class);
        _embeddeds.finalizeValueOnRead();
        autoExport = objects.property(Boolean.class).convention(true);
        applyImportPolicies = objects.property(Boolean.class).convention(true);
        scanCordaClasses = objects.property(String.class).value(_cordaClasses.map(this::generateCordaClassQuery));
        scanCordaClasses.finalizeValueOnRead();

        final Project project = jar.getProject();
        final Provider<String> groupName = project.provider(() -> project.getGroup().toString().trim());
        final Provider<String> archiveName = createArchiveName(jar);
        symbolicName = objects.property(String.class).convention(groupName.zip(archiveName, (group, name) ->
            group.isEmpty() ? name : group + '.' + name

        ));

        // Install a "listener" for files copied into the CorDapp jar.
        // We will extract the names of the non-empty packages inside
        // this jar as we go...
        jar.getRootSpec().eachFile(file -> {
            if (!file.isDirectory()) {
                final String[] elements = file.getRelativePath().getSegments();
                final int[] packageRange = getPackageRange(elements);
                if (packageRange[1] > packageRange[0]) {
                    final String[] packageName = copyOfRange(elements, packageRange[0], packageRange[1]);
                    final String firstElement = packageName[0];
                    if (!META_INF.equals(firstElement)
                            && !OSGI_INF.equals(firstElement)
                            && !"migration".equals(firstElement)
                            && isJavaIdentifiers(asList(packageName))) {
                        _autoExportPackages.add(String.join(".", packageName));
                    }
                }
            }
        });

        // Add the auto-extracted package names to the exports.
        _exports.addAll(isAutoExported());

        /*
         * Read an optional configuration file from a "friend" plugin:
         * ```
         *     net.corda.cordapp.cordapp-configuration
         * ```
         * This will allow us to keep our Bnd metadata instructions
         * up-to-date with future versions of Corda without also needing
         * to update the `cordapp-cpk2` plugin itself. (Hopefully, anyway.)
         */
        configured = false;
        configure(project.getRootProject());
    }

    @Internal // Annotated for documentation purposes only.
    public boolean getConfigured() {
        return configured;
    }

    public void exportPackages(@NotNull Provider<? extends Iterable<String>> packageNames) {
        _exports.addAll(packageNames);
    }

    public void exportPackages(@NotNull Iterable<String> packageNames) {
        _exports.addAll(packageNames);
    }

    public void exportPackage(@NotNull String... packageNames) {
        exportPackages(asList(packageNames));
    }

    public void exportPackage(@NotNull Provider<String> packageName) {
        _exports.add(packageName);
    }

    public void importPackages(@NotNull Provider<? extends Iterable<String>> packageNames) {
        _imports.addAll(packageNames);
    }

    public void importPackages(@NotNull Iterable<String> packageNames) {
        _imports.addAll(packageNames);
    }

    public void importPackage(@NotNull String... packageNames) {
        importPackages(asList(packageNames));
    }

    public void importPackage(@NotNull Provider<String> packageName) {
        _imports.add(packageName);
    }

    public void optionalImports(@NotNull Iterable<String> packageNames) {
        importPackages(map(packageNames, OsgiExtension::optional));
    }

    public void optionalImports(@NotNull Provider<? extends Iterable<String>> packageNames) {
        importPackages(packageNames.map(names -> map(names, OsgiExtension::optional) ));
    }

    public void optionalImport(@NotNull String... packageNames) {
        optionalImports(asList(packageNames));
    }

    public void optionalImport(@NotNull Provider<String> packageName) {
        importPackage(packageName.map(OsgiExtension::optional));
    }

    public void suppressImportVersions(@NotNull Iterable<String> packageNames) {
        optionalImports(map(packageNames, OsgiExtension::emptyVersion));
    }

    public void suppressImportVersion(@NotNull String[] packageNames) {
        suppressImportVersions(asList(packageNames));
    }

    public void suppressImportVersion(@NotNull Provider<String> packageName) {
        optionalImport(packageName.map(OsgiExtension::emptyVersion));
    }

    public void embed(@NotNull Provider<? extends Set<FileSystemLocation>> files) {
        _embeddeds.addAll(files);
    }

    @Input
    @NotNull
    public Property<Boolean> getAutoExport() {
        return autoExport;
    }

    @NotNull
    private Provider<? extends Set<String>> isAutoExported() {
        return autoExport.flatMap(isAuto ->
            isAuto ? _autoExportPackages : _noPackages
        );
    }

    @Input
    @NotNull
    public Provider<String> getExports() {
        return _exports.map(names ->
            names.isEmpty() ? "" : "-exportcontents:" + String.join(",", names)
        );
    }

    @Input
    @NotNull
    public Provider<String> getEmbeddedJars() {
        return _embeddeds.map(this::declareEmbeddedJars);
    }

    @NotNull
    private String declareEmbeddedJars(@NotNull Set<FileSystemLocation> locations) {
        if (locations.isEmpty()) {
            return "";
        } else {
            final StringJoiner includeResource = new StringJoiner(",", "-includeresource.cordapp:", System.lineSeparator());
            final StringJoiner bundleClassPath = new StringJoiner(",", BUNDLE_CLASSPATH + '=', System.lineSeparator()).add(".");
            for (FileSystemLocation location: locations) {
                final File file = location.getAsFile();
                final String embeddedJar = "lib/" + file.getName();
                includeResource.add(embeddedJar + '=' + file.toURI());
                bundleClassPath.add(embeddedJar);
            }
            return includeResource.toString() + bundleClassPath;
        }
    }

    @Input
    @NotNull
    public Property<Boolean> getApplyImportPolicies() {
        return applyImportPolicies;
    }

    @NotNull
    private Provider<? extends Map<String, String>> getActivePolicies() {
        return applyImportPolicies.flatMap(isActive ->
            isActive ? _packagePolicies : _noPolicies
        );
    }

    @Input
    @NotNull
    public Provider<String> getImports() {
        return _imports.zip(_requiredPackages, (imports, required) -> {
            final Set<String> result = new LinkedHashSet<>(imports);
            result.addAll(map(required, OsgiExtension::dynamic));
            return result;
        }).zip(getActivePolicies(), (imports, policy) -> {
            imports.addAll(map(policy.entrySet(), p -> consumerPolicy(p.getKey(), p.getValue())));
            return imports;
        }).map(this::declareImports);
    }

    @NotNull
    private String declareImports(@NotNull Set<String> importPackages) {
        return importPackages.isEmpty() ? "" : IMPORT_PACKAGE + '=' + String.join(",", importPackages) + ",*";
    }

    @Input
    @NotNull
    public Provider<String> getScanCordaClasses() {
        return scanCordaClasses;
    }

    @NotNull
    private String generateCordaClassQuery(@NotNull Map<String, String> cordaClasses) {
        final List<String> classes = new LinkedList<>();
        for (Map.Entry<String, String> cordaClass : new TreeMap<>(cordaClasses).entrySet()) {
            // This NAMED filter only identifies "anonymous" classes.
            // Adding STATIC removes all inner classes as well.
            classes.add(cordaClass.getKey() + "=${classes;" + cordaClass.getValue() + ";CONCRETE;PUBLIC;STATIC;NAMED;!*\\.[\\\\d]+*}");
        }
        return String.join(System.lineSeparator(), classes);
    }

    @Input
    @NotNull
    public Property<String> getSymbolicName() {
        return symbolicName;
    }

    private void configure(@NotNull Project project) {
        project.getPlugins().withId(CORDAPP_CONFIG_PLUGIN_ID, plugin -> {
            final URL resource = plugin.getClass().getResource(CORDAPP_CONFIG_FILENAME);
            if (resource == null) {
                return;
            }

            final Map<String, String> config = loadConfig(resource);
            for (Map.Entry<String, String> entry : config.entrySet()) {
                final String key = entry.getKey();
                if (CORDA_CLASSES.matcher(key).matches()) {
                    _cordaClasses.put(key, entry.getValue());
                }
            }

            /*
             * Replace the default set of required packages with any new ones.
             */
            final String requiredPackages = config.get(REQUIRED_PACKAGES);
            if (requiredPackages != null) {
                _requiredPackages.set(parsePackages(requiredPackages));
            }

            /*
             * Apply our OSGi "consumer policy" when importing these packages.
             */
            final String importPolicyPackages = config.get(IMPORT_POLICY_PACKAGES);
            if (importPolicyPackages != null) {
                for (Map.Entry<String, Attrs> entry : parseHeader(importPolicyPackages).entrySet()) {
                    String versionRange = entry.getValue().get(VERSION_RANGE_ATTRIBUTE);
                    if (versionRange == null) {
                        versionRange = DEFAULT_IMPORT_POLICY;
                    }
                    _packagePolicies.put(entry.getKey(), versionRange);
                }
            }

            /*
             * Show we have received our configuration.
             */
            configured = true;
        });
    }

    @NotNull
    private Provider<String> createArchiveName(@NotNull Jar jar) {
        return jar.getArchiveBaseName().zip(jar.getArchiveAppendix().orElse(""), OsgiExtension::dashConcat)
            .zip(jar.getArchiveClassifier().orElse(""), OsgiExtension::dashConcat);
    }
}
