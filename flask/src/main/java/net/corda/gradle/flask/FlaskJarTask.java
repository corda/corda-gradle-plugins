package net.corda.gradle.flask;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GradleVersion;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static java.util.zip.Deflater.NO_COMPRESSION;
import static net.corda.flask.common.Flask.Constants.BUFFER_SIZE;
import static net.corda.flask.common.Flask.Constants.DEFAULT_LAUNCHER_NAME;
import static net.corda.flask.common.Flask.Constants.GRADLE_TASK_GROUP;
import static net.corda.flask.common.Flask.Constants.JAVA_AGENTS_FILE;
import static net.corda.flask.common.Flask.Constants.JVM_ARGUMENT_FILE;
import static net.corda.flask.common.Flask.Constants.LIBRARIES_FOLDER;
import static net.corda.flask.common.Flask.Constants.METADATA_FOLDER;
import static net.corda.flask.common.Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP;

@SuppressWarnings({ "UnstableApiUsage", "unused" })
public class FlaskJarTask extends AbstractArchiveTask {

    private static final String MINIMUM_GRADLE_VERSION = "6.0";

    static {
        if (GradleVersion.current().compareTo(GradleVersion.version(MINIMUM_GRADLE_VERSION)) < 0) {
            throw new GradleException(FlaskJarTask.class.getName() +
                    " requires Gradle " + MINIMUM_GRADLE_VERSION + " or newer.");
        }
    }

    private final Property<String> launcherClassName;

    @Input
    public Property<String> getLauncherClassName() {
        return launcherClassName;
    }

    private final Property<String> mainClassName;

    @Input
    public Property<String> getMainClassName() {
        return mainClassName;
    }

    private final ListProperty<String> jvmArgs;

    @Input
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    public void includeLibraries(Object... files) {
        into(LIBRARIES_FOLDER, (copySpec) -> copySpec.from(files));
    }

    private final NamedDomainObjectContainer<JavaAgent> javaAgents;

    @Nested
    public NamedDomainObjectCollection<JavaAgent> getJavaAgents() {
        return javaAgents;
    }

    public void javaAgents(@Nonnull Action<NamedDomainObjectCollection<JavaAgent>> action) {
        action.execute(javaAgents);
    }

    @Inject
    public FlaskJarTask(@Nonnull ObjectFactory objects) {
        setGroup(GRADLE_TASK_GROUP);
        setDescription("Creates an executable jar file, embedding all of its runtime dependencies and default JVM arguments");
        BasePluginConvention basePluginConvention = getProject().getConvention().getPlugin(BasePluginConvention.class);
        getDestinationDirectory().set(basePluginConvention.getLibsDirectory());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveExtension().convention("jar");
        launcherClassName = objects.property(String.class).convention(DEFAULT_LAUNCHER_NAME);
        mainClassName = objects.property(String.class);
        jvmArgs = objects.listProperty(String.class);
        javaAgents = objects.domainObjectContainer(JavaAgent.class);
        from(getProject().tarTree(LauncherResource.instance), copySpec -> exclude(JarFile.MANIFEST_NAME));

        Provider<File> heartbeatJarProvider = getProject().provider(() -> {
            File dest = new File(getTemporaryDir(), HeartbeatAgentResource.instance.getDisplayName());
            try (OutputStream os = Flask.write(dest, false); InputStream is = HeartbeatAgentResource.instance.read()) {
                Flask.write2Stream(is, os);
            }
            return dest;
        });
        includeLibraries(heartbeatJarProvider);
    }

    @Input
    public String getLauncherArchiveHash() {
        return Flask.bytes2Hex(Flask.computeSHA256Digest(LauncherResource.instance::read));
    }

    @Input
    public String getHeartbeatAgentHash() {
        return Flask.bytes2Hex(Flask.computeSHA256Digest(HeartbeatAgentResource.instance::read));
    }

    @RequiredArgsConstructor
    private static class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zoos;
        private final Manifest manifest;
        private final MessageDigest md;
        private final ZipEntryFactory zipEntryFactory;
        private final byte[] buffer;

        @Override
        @SneakyThrows
        public void processFile(FileCopyDetailsInternal fileCopyDetails) {
            String entryName = fileCopyDetails.getRelativePath().toString();
            if (!fileCopyDetails.isDirectory() && entryName.startsWith(LIBRARIES_FOLDER)) {
                Supplier<InputStream> streamSupplier = () -> Flask.read(fileCopyDetails.getFile(), false);
                Attributes attr = manifest.getEntries().computeIfAbsent(entryName, it -> new Attributes());
                md.reset();
                attr.putValue(Flask.ManifestAttributes.ENTRY_HASH,
                        Base64.getEncoder().encodeToString(Flask.computeDigest(streamSupplier, md, buffer)));
            }
            if (METADATA_FOLDER.equals(entryName)) return;
            if (fileCopyDetails.isDirectory()) {
                ZipEntry zipEntry = zipEntryFactory.createDirectoryEntry(entryName, fileCopyDetails.getLastModified());
                zoos.putNextEntry(zipEntry);
            } else {
                ZipEntry zipEntry = zipEntryFactory.createZipEntry(entryName, fileCopyDetails.getLastModified());
                boolean compressed = Flask.splitExtension(fileCopyDetails.getSourceName())
                        .map(entry -> ".jar".equals(entry.getValue()))
                        .orElse(false);
                if (!compressed) {
                    zipEntry.setMethod(ZipEntry.DEFLATED);
                } else {
                    try (InputStream is = Flask.read(fileCopyDetails.getFile(), false)) {
                        Flask.computeSizeAndCrc32(zipEntry, is, buffer);
                    }
                    zipEntry.setMethod(ZipEntry.STORED);
                }
                zoos.putNextEntry(zipEntry);
                try (InputStream is = Flask.read(fileCopyDetails.getFile(), false)) {
                    Flask.write2Stream(is, zoos, buffer);
                }
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    @RequiredArgsConstructor
    private static final class ZipEntryFactory {

        private final boolean isPreserveFileTimestamps;
        private final long defaultLastModifiedTime;

        @Nonnull
        ZipEntry createZipEntry(String entryName, long lastModifiedTime) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zipEntry.setTime(isPreserveFileTimestamps ? lastModifiedTime : ZIP_ENTRIES_DEFAULT_TIMESTAMP);
            return zipEntry;
        }

        @Nonnull
        ZipEntry createZipEntry(String entryName) {
            return createZipEntry(entryName, defaultLastModifiedTime);
        }

        @Nonnull
        ZipEntry createDirectoryEntry(@Nonnull String entryName, long lastModifiedTime) {
            ZipEntry zipEntry = createZipEntry(entryName.endsWith("/") ? entryName : entryName + '/', lastModifiedTime);
            zipEntry.setMethod(ZipEntry.STORED);
            zipEntry.setCompressedSize(0);
            zipEntry.setSize(0);
            zipEntry.setCrc(0);
            return zipEntry;
        }

        @Nonnull
        ZipEntry createDirectoryEntry(@Nonnull String entryName) {
            return createDirectoryEntry(entryName, defaultLastModifiedTime);
        }

        @Nonnull
        ZipEntry copyOf(@Nonnull ZipEntry zipEntry) {
            if (zipEntry.getMethod() == ZipEntry.STORED) {
                return new ZipEntry(zipEntry);
            } else {
                ZipEntry newEntry = new ZipEntry(zipEntry.getName());
                newEntry.setMethod(ZipEntry.DEFLATED);
                newEntry.setTime(zipEntry.getTime());
                newEntry.setExtra(zipEntry.getExtra());
                newEntry.setComment(zipEntry.getComment());
                return newEntry;
            }
        }
    }

    @Override
    @Nonnull
    protected CopyAction createCopyAction() {
        File destination = getArchiveFile().get().getAsFile();
        return new CopyAction() {

            private final ZipEntryFactory zipEntryFactory = new ZipEntryFactory(isPreserveFileTimestamps(), System.currentTimeMillis());

            @Override
            @Nonnull
            @SneakyThrows
            public WorkResult execute(@Nonnull CopyActionProcessingStream copyActionProcessingStream) {
                Manifest manifest = new Manifest();
                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                mainAttributes.put(Attributes.Name.MAIN_CLASS, DEFAULT_LAUNCHER_NAME);
                mainAttributes.putValue(Flask.ManifestAttributes.LAUNCHER_CLASS, launcherClassName.get());
                mainAttributes.putValue(Flask.ManifestAttributes.PREMAIN_CLASS, DEFAULT_LAUNCHER_NAME);

                /**
                 * {@link mainClassName} can never be null as its getter is annotated with @Input and
                 * task invocation fails if a non {@link org.gradle.api.tasks.Optional} annotated task input is null
                 */
                String mainClass = mainClassName.getOrNull();
                mainAttributes.putValue(Flask.ManifestAttributes.APPLICATION_CLASS, mainClass);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[BUFFER_SIZE];
                mainAttributes.putValue(Flask.ManifestAttributes.HEARTBEAT_AGENT_HASH,
                    Flask.bytes2Hex(Flask.computeDigest(HeartbeatAgentResource.instance::read, md, buffer)));

                /**
                 * The manifest has to be the first zip entry in a jar archive, as an example,
                 * {@link java.util.jar.JarInputStream} assumes the manifest is the first (or second at most)
                 * entry in the jar and simply returns a null manifest if that is not the case.
                 * In this case the manifest has to contain the hash of all the jar entries, so it cannot
                 * be computed in advance, we write all the entries to a temporary zip archive while computing the manifest,
                 * then we write the manifest to the final zip file as the first entry and, finally,
                 * we copy all the other entries from the temporary archive.
                 *
                 * The {@link org.gradle.api.Task#getTemporaryDir} directory is guaranteed
                 * to be unique per instance of this task.
                 */
                File temporaryJar = new File(getTemporaryDir(), "premature.zip");
                try (ZipOutputStream zipOutputStream = new ZipOutputStream(Flask.write(temporaryJar, true))) {
                    zipOutputStream.setLevel(NO_COMPRESSION);
                    StreamAction streamAction = new StreamAction(zipOutputStream, manifest, md, zipEntryFactory, buffer);
                    copyActionProcessingStream.process(streamAction);
                }

                try (ZipOutputStream zipOutputStream = new ZipOutputStream(Flask.write(destination, true));
                     ZipInputStream zipInputStream = new ZipInputStream(Flask.read(temporaryJar, true))) {
                    zipOutputStream.setLevel(BEST_COMPRESSION);
                    ZipEntry zipEntry = zipEntryFactory.createDirectoryEntry(METADATA_FOLDER);
                    zipOutputStream.putNextEntry(zipEntry);
                    zipEntry = zipEntryFactory.createZipEntry(JarFile.MANIFEST_NAME);
                    zipEntry.setMethod(ZipEntry.DEFLATED);
                    zipOutputStream.putNextEntry(zipEntry);
                    manifest.write(zipOutputStream);

                    List<String> df = Optional.ofNullable(jvmArgs.getOrNull())
                            .filter(it -> !it.isEmpty()).orElse(null);
                    if (df != null) {
                        Properties jvmArgsPropertyFile = new Properties();
                        for (int i = 0; i < df.size(); i++) {
                            String jvmArg = df.get(i);
                            jvmArgsPropertyFile.setProperty(Integer.toString(i), jvmArg);
                        }
                        zipEntry = zipEntryFactory.createZipEntry(JVM_ARGUMENT_FILE);
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                        zipOutputStream.putNextEntry(zipEntry);
                        Flask.storeProperties(jvmArgsPropertyFile, zipOutputStream);
                    }

                    if (!javaAgents.isEmpty()) {
                        Properties javaAgentPropertyFile = new Properties();
                        int index = 0;
                        for(JavaAgent javaAgent : javaAgents) {
                            md.reset();
                            Supplier<InputStream> streamSupplier = () -> Flask.read(javaAgent.getJar().get().getAsFile(), false);
                            StringBuilder sb = new StringBuilder();
                            sb.append(Flask.bytes2Hex(Flask.computeDigest(streamSupplier, md, buffer)));
                            if (javaAgent.getArgs().isPresent()) {
                                sb.append('=');
                                sb.append(javaAgent.getArgs().get());
                            }
                            javaAgentPropertyFile.setProperty(Integer.toString(index++), sb.toString());
                        }
                        zipEntry = zipEntryFactory.createZipEntry(JAVA_AGENTS_FILE);
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                        zipOutputStream.putNextEntry(zipEntry);
                        Flask.storeProperties(javaAgentPropertyFile, zipOutputStream);
                    }

                    while (true) {
                        zipEntry = zipInputStream.getNextEntry();
                        if (zipEntry == null) break;
                        // Create a new ZipEntry explicitly, without relying on
                        // subtle (undocumented?) behaviour of ZipInputStream.
                        zipOutputStream.putNextEntry(zipEntryFactory.copyOf(zipEntry));
                        Flask.write2Stream(zipInputStream, zipOutputStream, buffer);
                    }
                    return () -> true;
                }
            }
        };
    }
}