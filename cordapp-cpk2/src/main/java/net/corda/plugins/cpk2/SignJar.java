package net.corda.plugins.cpk2;

import net.corda.plugins.cpk2.signing.SigningOptions;
import net.corda.plugins.cpk2.signing.SigningOptions.Key;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.map;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@SuppressWarnings("unused")
@DisableCachingByDefault
public class SignJar extends DefaultTask {
    private static final String DUMMY_VALUE = "****";

    @SuppressWarnings("SameParameterValue")
    private static void writeResourceToFile(@NotNull String resourcePath, @NotNull Path path) {
        final URL resource = SignJar.class.getClassLoader().getResource(resourcePath);
        if (resource != null) {
            try (InputStream input = resource.openStream()) {
                Files.copy(input, path, REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static void sign(
        @NotNull Task task,
        @NotNull SigningOptions signing,
        @NotNull File file
    ) {
        sign(task, signing, file, null);
    }

    public static void sign(
        @NotNull Task task,
        @NotNull SigningOptions signing,
        @NotNull File file,
        @Nullable File outputFile
    ) {
        final Map<String, String> options = signing.getSignJarOptions().get();
        final boolean useDefaultKeyStore = !signing.getKeyStore().isPresent();
        final Logger logger = task.getLogger();
        try {
            if (useDefaultKeyStore) {
                logger.info("CorDapp JAR signing with the default Corda development key, suitable for Corda running in development mode only.");
                final Path keyStore = File.createTempFile(
                    SigningOptions.DEFAULT_KEYSTORE_FILE,
                    SigningOptions.DEFAULT_KEYSTORE_EXTENSION,
                    task.getTemporaryDir()
                ).toPath();
                writeResourceToFile(SigningOptions.DEFAULT_KEYSTORE, keyStore);
                options.put(Key.KEYSTORE, keyStore.toString());
            }

            final Path path = file.toPath();
            options.put(Key.JAR, path.toString());

            if (outputFile != null) {
                options.put(Key.SIGNEDJAR, outputFile.toPath().toString());
            }

            logger.info("Jar signing with following options: {}", toSanitized(options));
            try {
                task.getAnt().invokeMethod("signjar", options);
            } catch (Exception e) {
                // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
                throw new InvalidUserDataException("Exception while signing " + path.getFileName() + ", " +
                    "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                    "or disable signing by 'cordapp.signing.enabled false'. " +
                    ((logger.isInfoEnabled() || logger.isDebugEnabled())
                        ? "Search for 'ant:signjar' in log output."
                        : "Run with --info or --debug option and search for 'ant:signjar' in log output. "), e);
            } finally {
                if (useDefaultKeyStore) {
                    final String jarFile = options.get(Key.KEYSTORE);
                    if (jarFile != null) {
                        Files.deleteIfExists(Paths.get(jarFile));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @NotNull
    private static Map<String, String> toSanitized(@NotNull Map<String, String> map) {
        final Map<String, String> result = new LinkedHashMap<>(map);
        result.computeIfPresent(Key.KEYPASS, (k, v) -> DUMMY_VALUE);
        result.computeIfPresent(Key.STOREPASS, (k, v) -> DUMMY_VALUE);
        return result;
    }

    private final SigningOptions signing;
    private final Property<String> postfix;
    private final ConfigurableFileCollection _inputJars;
    private final ConfigurableFileCollection _outputJars;

    @Inject
    public SignJar(@NotNull ObjectFactory objects) {
        setDescription("Signs the given jars, using the cordapp.signing.options key by default.");
        setGroup(CORDAPP_TASK_GROUP);

        final CordappExtension cordapp = getProject().getExtensions().findByType(CordappExtension.class);
        if (cordapp == null) {
            throw new GradleException("Please apply cordapp-cpk2 plugin to create cordapp DSL extension.");
        }
        signing = objects.newInstance(SigningOptions.class).values(cordapp.getSigning().getOptions());
        postfix = objects.property(String.class).convention("-signed");
        _inputJars = objects.fileCollection();
        _outputJars = objects.fileCollection();
        _outputJars.setFrom(_inputJars.getElements().map(files -> map(files, this::toSigned)));
        _outputJars.disallowChanges();
    }

    @Nested
    @NotNull
    public SigningOptions getSigning() {
        return signing;
    }

    public void signing(@NotNull Action<? super SigningOptions> action) {
        action.execute(signing);
    }

    @Input
    @NotNull
    public Property<String> getPostfix() {
        return postfix;
    }

    @PathSensitive(RELATIVE)
    @SkipWhenEmpty
    @InputFiles
    @NotNull
    public FileCollection getInputJars() {
        return _inputJars;
    }

    public void setInputJars(Object... jars) {
        _inputJars.setFrom(jars);
    }

    public void inputJars(Object... jars) {
        _inputJars.setFrom(jars);
    }

    @OutputFiles
    @NotNull
    public FileCollection getOutputJars() {
        return _outputJars;
    }

    @NotNull
    private Provider<File> toSigned(@NotNull FileSystemLocation file) {
        return toSigned(file.getAsFile());
    }

    @NotNull
    private Provider<File> toSigned(@NotNull File file) {
        return postfix.map(pfx ->
            new File(addSuffix(file.getAbsolutePath(), pfx))
        );
    }

    @NotNull
    private String addSuffix(@NotNull String path, @NotNull String suffix) {
        final int lastDot = path.lastIndexOf('.');
        return lastDot == -1 ? path + suffix : path.substring(0, lastDot) + suffix + path.substring(lastDot);
    }

    @TaskAction
    public void build() {
        for (File file : getInputJars()) {
            sign(this, signing, file, toSigned(file).get());
        }
    }
}
