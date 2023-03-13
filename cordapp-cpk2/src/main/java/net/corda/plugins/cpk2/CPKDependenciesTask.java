package net.corda.plugins.cpk2;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import javax.inject.Inject;

import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_TASK_GROUP;
import static net.corda.plugins.cpk2.CordappUtils.CORDA_CPK_TYPE;
import static net.corda.plugins.cpk2.CordappUtils.CPK_DEPENDENCIES;
import static net.corda.plugins.cpk2.CordappUtils.digestFor;
import static net.corda.plugins.cpk2.CordappUtils.hashFor;
import static org.gradle.api.tasks.PathSensitivity.RELATIVE;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.BUNDLE_VERSION;

@DisableCachingByDefault
public class CPKDependenciesTask extends DefaultTask {
    private static final String CPK_DEPENDENCIES_FORMAT_VERSION2 = "2.0";

    private final Property<String> hashAlgorithm;
    private final ConfigurableFileCollection _projectCpks;
    private final ConfigurableFileCollection _remoteCpks;
    private final DirectoryProperty outputDir;
    private final Provider<RegularFile> cpkOutput;

    @Inject
    public CPKDependenciesTask(@NotNull ObjectFactory objects) {
        setDescription("Records this CorDapp's CPK dependencies.");
        setGroup(CORDAPP_TASK_GROUP);

        hashAlgorithm = objects.property(String.class);
        _projectCpks = objects.fileCollection();
        _remoteCpks = objects.fileCollection();
        outputDir = objects.directoryProperty();
        cpkOutput = outputDir.file(CPK_DEPENDENCIES);
    }

    @Input
    @NotNull
    public Property<String> getHashAlgorithm() {
        return hashAlgorithm;
    }

    @PathSensitive(RELATIVE)
    @InputFiles
    @NotNull
    public FileCollection getProjectCpks() {
        return _projectCpks;
    }

    @PathSensitive(RELATIVE)
    @InputFiles
    @NotNull
    public FileCollection getRemoteCpks() {
        return _remoteCpks;
    }

    @Internal
    @NotNull
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    @OutputFile
    @NotNull
    public Provider<RegularFile> getCpkOutput() {
        return cpkOutput;
    }

    /**
     * Don't eagerly configure the {@link DependencyCalculator} task, even if
     * someone eagerly configures this {@link CPKDependenciesTask} by accident.
     */
    void setCPKsFrom(@NotNull TaskProvider<DependencyCalculator> task) {
        _projectCpks.setFrom(task.flatMap(DependencyCalculator::getProjectCordapps));
        _projectCpks.disallowChanges();
        _remoteCpks.setFrom(task.flatMap(DependencyCalculator::getRemoteCordapps));
        _remoteCpks.disallowChanges();
        dependsOn(task);
    }

    @TaskAction
    public void generate() {
        final MessageDigest digest = digestFor(hashAlgorithm.get().toUpperCase());

        try (
            // Write CPK dependency information as JSON document.
            PrintWriter pw = new PrintWriter(cpkOutput.get().getAsFile(), "UTF-8");
            JsonDependencyWriter writer = new JsonDependencyWriter(pw, digest)
        ) {
            final Logger logger = getLogger();
            for (File cpk: getProjectCpks()) {
                logger.info("Project CorDapp CPK dependency: {}", cpk.getName());
                writer.writeProjectDependency(cpk);
            }

            for (File cpk: getRemoteCpks()) {
                logger.info("Remote CorDapp CPK dependency: {}", cpk.getName());
                writer.writeRemoteDependency(cpk);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserDataException(e.getMessage(), e);
        }
    }

    private static final class JsonDependencyWriter implements AutoCloseable {
        private final PrintWriter output;
        private final MessageDigest digest;
        private final Base64.Encoder encoder;
        private boolean firstElement;

        JsonDependencyWriter(@NotNull PrintWriter output, @NotNull MessageDigest digest) {
            this.output = output;
            this.digest = digest;
            encoder = Base64.getEncoder();
            firstElement = true;

            output.print("{\"formatVersion\":\"");
            output.print(CPK_DEPENDENCIES_FORMAT_VERSION2);
            output.print("\",\"dependencies\":[");
        }

        // Escape slashes and quotes inside string
        @NotNull
        private String escapeString(@NotNull String s) {
            return s.replace("\\", "\\\\")  // \ replaced with \\
                .replace("\"", "\\\"");  // " replaced with \"
        }

        private void writeCommonElements(@NotNull JarFile jar) throws IOException {
            final Attributes mainAttributes = jar.getManifest().getMainAttributes();
            output.print("\"name\":\"");
            output.print(escapeString(mainAttributes.getValue(BUNDLE_SYMBOLICNAME)));
            output.print("\",\"version\":\"");
            output.print(escapeString(mainAttributes.getValue(BUNDLE_VERSION)));
            output.print("\",");
            final String cpkType = mainAttributes.getValue(CORDA_CPK_TYPE);
            if (cpkType != null) {
                output.print("\"type\":\"");
                output.print(escapeString(cpkType));
                output.print("\",");
            }
        }

        void writeProjectDependency(@NotNull File jar) throws IOException {
            openDependency();
            try (JarFile jarFile = new JarFile(jar)) {
                writeCommonElements(jarFile);
            }
            output.print("\"verifySameSignerAsMe\":true");
            closeDependency();
        }

        void writeRemoteDependency(@NotNull File jar) throws IOException {
            openDependency();
            try (JarFile jarFile = new JarFile(jar)) {
                writeCommonElements(jarFile);
            }
            final byte[] hash;
            try (InputStream input = Files.newInputStream(jar.toPath())) {
                hash = hashFor(digest, input);
            }
            output.print("\"verifyFileHash\":{\"algorithm\":\"");
            output.print(digest.getAlgorithm());
            output.print("\",\"fileHash\":\"");
            output.print(encoder.encodeToString(hash));
            output.print("\"}");
            closeDependency();
        }

        private void openDependency() {
            if (firstElement) {
                firstElement = false;
            } else {
                output.print(',');
            }
            output.print('{');
        }

        private void closeDependency() {
            output.print('}');
        }

        @Override
        public void close() {
            output.print("]}");
        }
    }
}
