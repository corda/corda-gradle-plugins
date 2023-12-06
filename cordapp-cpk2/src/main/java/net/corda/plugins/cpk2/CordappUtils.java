package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static aQute.bnd.osgi.Constants.FIXUPMESSAGES_IS_DIRECTIVE;
import static aQute.bnd.osgi.Constants.FIXUPMESSAGES_IS_ERROR;
import static aQute.bnd.osgi.Constants.FIXUPMESSAGES_IS_WARNING;
import static aQute.bnd.osgi.Constants.FIXUPMESSAGES_RESTRICT_DIRECTIVE;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Collections.max;
import static java.util.Collections.unmodifiableMap;
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME;

public final class CordappUtils {
    static final String CORDAPP_CPK_PLUGIN_ID = "net.corda.plugins.cordapp-cpk2";
    static final String CORDAPP_CONFIG_PLUGIN_ID = "net.corda.cordapp.cordapp-configuration";
    public static final String CORDAPP_TASK_GROUP = "Cordapp";

    static final String CORDA_API_GROUP = "net.corda";
    static final String ENTERPRISE_API_GROUP = "com.r3.corda";

    static final String CORDAPP_SEALING_SYSTEM_PROPERTY_NAME = "net.corda.cordapp.sealing.enabled";

    public static final String CPK_FILE_EXTENSION = "jar";

    static final String CORDAPP_CONFIGURATION_NAME = "cordapp";
    static final String CORDAPP_EXTERNAL_CONFIGURATION_NAME = "cordappExternal";
    static final String CORDAPP_PACKAGING_CONFIGURATION_NAME = "cordappPackaging";
    static final String CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly";
    static final String CORDA_ALL_PROVIDED_CONFIGURATION_NAME = "cordaAllProvided";
    static final String CORDA_PRIVATE_CONFIGURATION_NAME = "cordaPrivateProvided";
    static final String CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided";
    static final String CORDA_EMBEDDED_CONFIGURATION_NAME = "cordaEmbedded";
    public static final String ALL_CORDAPPS_CONFIGURATION_NAME = "allCordapps";
    static final String CORDA_CPK_CONFIGURATION_NAME = "cordaCPK";

    static final String CPK_DEPENDENCIES = "META-INF/CPKDependencies.json";

    // These tags are for the CPK file.
    static final String CPK_PLATFORM_VERSION = "Corda-CPK-Built-Platform-Version";
    public static final String CPK_CORDAPP_NAME = "Corda-CPK-Cordapp-Name";
    static final String CPK_CORDAPP_VERSION = "Corda-CPK-Cordapp-Version";
    static final String CPK_CORDAPP_LICENCE = "Corda-CPK-Cordapp-Licence";
    static final String CPK_CORDAPP_VENDOR = "Corda-CPK-Cordapp-Vendor";
    static final String CPK_FORMAT_TAG = "Corda-CPK-Format";
    static final String CPK_FORMAT = "2.0";

    // These tags are for the "main" JAR file.
    static final int PLATFORM_VERSION_X = 999;
    static final String IMPORT_POLICY_PACKAGES = "Import-Policy-Packages";
    static final String REQUIRED_PACKAGES = "Required-Packages";

    static final String CORDAPP_PLATFORM_VERSION = "Cordapp-Built-Platform-Version";
    static final String CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name";
    static final String CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version";
    static final String CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name";
    static final String CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version";
    public static final String CORDA_CPK_TYPE = "Corda-CPK-Type";

    static final String CLASSES_IN_WRONG_DIRECTORY_FIXUP
        = "\"Classes found in the wrong directory\";"
            + FIXUPMESSAGES_RESTRICT_DIRECTIVE + '=' + FIXUPMESSAGES_IS_ERROR + ';'
            + FIXUPMESSAGES_IS_DIRECTIVE + '=' + FIXUPMESSAGES_IS_WARNING;

    /*
     * Location of official R3 documentation for building CPKs and CPBs.
     */
    static final String CORDAPP_DOCUMENTATION_URL = "https://docs.corda.net/cordapp-build-systems.html";

    static final String SEPARATOR = System.lineSeparator() + "- ";

    @NotNull
    static <T, R> List<R> map(@NotNull Iterable<T> items, @NotNull Function<T, R> operator) {
        return mapTo(new LinkedList<>(), items, operator);
    }

    @NotNull
    static <T, R, C extends Collection<R>> C mapTo(
        @NotNull C collection,
        @NotNull Iterable<T> items,
        @NotNull Function<T, R> operator
    ) {
        for (T item : items) {
            collection.add(operator.apply(item));
        }
        return collection;
    }

    @NotNull
    static <T, R, C extends Collection<R>> C flatMapTo(
        @NotNull C collection,
        @NotNull Iterable<T> items,
        @NotNull Function<T, ? extends Iterable<R>> transform
    ) {
        for (T item : items) {
            transform.apply(item).iterator().forEachRemaining(collection::add);
        }
        return collection;
    }

    @NotNull
    static <T, R> List<R> filterIsInstance(@NotNull Iterable<T> items, @NotNull Class<R> clazz) {
        final List<R> result = new LinkedList<>();
        for (T item : items) {
            if (clazz.isInstance(item)) {
                result.add(clazz.cast(item));
            }
        }
        return result;
    }

    @NotNull
    static String toMaven(@NotNull Dependency dependency) {
        final StringBuilder builder = new StringBuilder();
        final String group = dependency.getGroup();
        if (group != null) {
            builder.append(group).append(':');
        }
        builder.append(dependency.getName());
        final String version = dependency.getVersion();
        if (version != null) {
            builder.append(':').append(version);
        }
        return builder.toString();
    }

    @NotNull
    static Configuration createBasicConfiguration(
        @NotNull ConfigurationContainer container,
        @NotNull String name
    ) {
        final Configuration configuration = container.maybeCreate(name).setVisible(false);
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(false);
        return configuration;
    }

    /*
     * This configuration will contribute to the CorDapp's compile classpath
     * but not to its runtime classpath. However, it will still contribute
     * to all TESTING classpaths.
     */
    @NotNull
    static Configuration createCompileConfiguration(@NotNull ConfigurationContainer container, @NotNull String name) {
        return createCompileConfiguration(container, name, "Implementation");
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    private static Configuration createCompileConfiguration(
        @NotNull ConfigurationContainer container,
        @NotNull String name,
        @NotNull String testSuffix
    ) {
        final Configuration configuration = createBasicConfiguration(container, name);
        container.getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration);
        container.matching(cfg -> cfg.getName().endsWith(testSuffix)).configureEach(cfg ->
            cfg.extendsFrom(configuration)
        );
        return configuration;
    }

    // Configuration.setCanBeDeclared(boolean) was introduced in Gradle 8.2.
    public static void setCannotBeDeclared(@NotNull Configuration configuration) {
        try {
            MethodHandles.publicLookup()
                .findVirtual(configuration.getClass(), "setCanBeDeclared", methodType(void.class, boolean.class))
                .invoke(configuration, false);
        } catch (Error e) {
            throw e;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Identify the artifacts that were resolved for these {@link Dependency} objects,
     * including all of their transitive dependencies.
     * @return The unique {@link ResolvedArtifact}s.
     */
    @NotNull
    static Set<ResolvedArtifact> resolveAll(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies
    ) {
        return resolve(resolved, dependencies, ResolvedDependency::getAllModuleArtifacts);
    }

    /**
     * Identify the artifacts that were resolved for these {@link Dependency} objects only.
     * This does not include any transitive dependencies.
     * @return The unique {@link ResolvedArtifact}s.
     */
    @NotNull
    static Set<ResolvedArtifact> resolveFirstLevel(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies
    ) {
        return resolve(resolved, dependencies, ResolvedDependency::getModuleArtifacts);
    }

    @NotNull
    private static Set<ResolvedArtifact> resolve(
        @NotNull ResolvedConfiguration resolved,
        @NotNull Collection<? extends Dependency> dependencies,
        @NotNull Function<ResolvedDependency, Iterable<ResolvedArtifact>> fetchArtifacts
    ) {
        return flatMapTo(
            new LinkedHashSet<>(),
            resolved.getFirstLevelModuleDependencies(dependencies::contains),
            fetchArtifacts
        );
    }

    @NotNull
    static <T> Set<T> subtract(@NotNull Set<T> originals, @NotNull Collection<T> unwanted) {
        final Set<T> result = new LinkedHashSet<>(originals);
        result.removeAll(unwanted);
        return result;
    }

    /**
     * @return The {@link Manifest} from a jar file.
     */
    @NotNull
    static Manifest manifestOf(@NotNull File file) {
        try (JarFile jarFile = new JarFile(file)) {
            return jarFile.getManifest();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return The maximum value of {@code attributeName} from all {@link Manifest}s, or
     * {@code defaultValue} if no such value can be derived. The attribute is assumed to
     * have an integer value.
     */
    static int maxOf(@NotNull Iterable<File> files, @NotNull String attributeName, int defaultValue) {
        final Collection<Integer> candidates = new HashSet<>();
        for (File file : files) {
            final String value = manifestOf(file).getMainAttributes().getValue(attributeName);
            if (value != null) {
                try {
                    candidates.add(Integer.valueOf(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return candidates.isEmpty() ? defaultValue : max(candidates);
    }

    @NotNull
    static String joinToString(@NotNull Iterable<?> items, @NotNull CharSequence delimiter) {
        return joinToString(items, delimiter, "");
    }

    @NotNull
    static String joinToString(@NotNull Iterable<?> items, CharSequence delimiter, @NotNull CharSequence prefix) {
        final StringJoiner joiner = new StringJoiner(delimiter, prefix, "");
        for (Object item: items) {
            joiner.add(item.toString());
        }
        return joiner.toString();
    }

    @NotNull
    public static Action<TaskExecutionGraph> copyJarEnabledTo(@NotNull Task target) {
        return new CopyEnabledAction(target, Jar.class, JAR_TASK_NAME);
    }

    private static final Pattern MULTI_RELEASE = Pattern.compile("^META-INF/versions/\\d++/(.++)$");
    private static final char DIRECTORY_SEPARATOR = '/';
    private static final char PACKAGE_SEPARATOR = '.';

    /**
     * @return The package names from a {@link JarFile}.
     */
    @NotNull
    static Set<String> getPackages(@NotNull File file) {
        try (JarFile jar = new JarFile(file)) {
            final Set<String> packages = new LinkedHashSet<>();
            final Enumeration<JarEntry> jarEntries = jar.entries();
            while (jarEntries.hasMoreElements()) {
                final JarEntry jarEntry = jarEntries.nextElement();
                if (!jarEntry.isDirectory()) {
                    final String entryName = jarEntry.getName();
                    if (entryName.startsWith("OSGI-INF/")) {
                        continue;
                    }

                    final String binaryFQN;
                    if (entryName.startsWith("META-INF/")) {
                        final Matcher matcher = MULTI_RELEASE.matcher(entryName);
                        if (!matcher.matches()) {
                            continue;
                        }
                        binaryFQN = matcher.group(1);
                    } else {
                        binaryFQN = entryName;
                    }
                    final int lastIdx = binaryFQN.lastIndexOf(DIRECTORY_SEPARATOR);
                    final String binaryPackageName = lastIdx == -1 ? binaryFQN : binaryFQN.substring(0, lastIdx);
                    if (isValidPackage(binaryPackageName)) {
                        packages.add(toPackageName(binaryPackageName));
                    }
                }
            }
            return packages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isValidPackage(@NotNull String name) {
        return Arrays.stream(name.split(String.valueOf(DIRECTORY_SEPARATOR)))
            .allMatch(CordappUtils::isJavaIdentifier);
    }

    @NotNull
    private static String toPackageName(@NotNull String resourceName) {
        return resourceName.replace(DIRECTORY_SEPARATOR, PACKAGE_SEPARATOR);
    }

    /**
     * Convert a {@link String} into a {@link Map} of 'key,value' pairs.
     * Any lines which do not match the patterns 'key = value'
     * or 'key: value' are skipped.
     * @return The set of {@code (key, value)} pairs.
     */
    @NotNull
    static Map<String, String> parseInstructions(@NotNull String instructions) {
        final Map<String, String> result = new LinkedHashMap<>();
        new BufferedReader(new StringReader(instructions)).lines()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(line -> {
                final String[] instruction = parseInstruction(line);
                if (instruction != null) {
                    result.put(instruction[0], instruction[1]);
                }
            });
        return unmodifiableMap(result);
    }

    @Nullable
    static String[] parseInstruction(@NotNull String instruction) {
        for (int idx = 0; idx < instruction.length(); ++idx) {
            final char c = instruction.charAt(idx);
            if (c == '=' || c == ':') {
                final String key = instruction.substring(0, idx).trim();
                return key.isEmpty() ? null : new String[] {
                    key, instruction.substring(idx + 1).trim()
                };
            }
        }
        return null;
    }

    /**
     * Check whether every {@link String} in this {@link List} is
     * a valid Java identifier.
     * @return true if all members of {@code strs} is a valid Java identifier.
     */
    static boolean isJavaIdentifiers(@NotNull List<String> strs) {
        return strs.stream().allMatch(CordappUtils::isJavaIdentifier);
    }

    /**
     * Checks whether this {@link String} could be considered to
     * be a valid identifier in Java. Identifiers are only
     * permitted to contain a specific subset of {@code char}s.
     * @return true if {@code str} is a valid Java identifier.
     */
    static boolean isJavaIdentifier(@NotNull String str) {
        if (str.isEmpty() || !Character.isJavaIdentifierStart(str.charAt(0))) {
            return false;
        }

        int idx = str.length();
        while (--idx > 0) {
            if (!Character.isJavaIdentifierPart(str.charAt(idx))) {
                return false;
            }
        }

        return true;
    }

    /**
     * @param algorithmName The hashing algorithm to use.
     * @return A {@link MessageDigest} for {@code algorithmName},
     * handling any {@link NoSuchAlgorithmException}.
     */
    @NotNull
    public static MessageDigest digestFor(@NotNull String algorithmName) {
        try {
            return MessageDigest.getInstance(algorithmName);
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidUserDataException("Hash algorithm " + algorithmName + " not available");
        }
    }

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int EOF = -1;

    /*
     * @return Hash for contents of {@link InputStream}.
     */
    @NotNull
    public static byte[] hashFor(@NotNull MessageDigest digest, @NotNull InputStream input) throws IOException {
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while (true) {
            final int length = input.read(buffer);
            if (length == EOF) {
                break;
            }
            digest.update(buffer, 0, length);
        }
        return digest.digest();
    }

    static boolean isNullOrEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    @NotNull
    static Element appendElement(@NotNull Element element, @NotNull String name) {
        final Element childElement = element.getOwnerDocument().createElement(name);
        element.appendChild(childElement);
        return childElement;
    }

    @NotNull
    static Element appendElement(@NotNull Element element, @NotNull String name, @Nullable String value) {
        final Element child = appendElement(element, name);
        child.appendChild(element.getOwnerDocument().createTextNode(value));
        return child;
    }
}
