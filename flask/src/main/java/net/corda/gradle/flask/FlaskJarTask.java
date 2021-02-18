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
        into(Flask.Constants.LIBRARIES_FOLDER, (copySpec) -> copySpec.from(files));
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
        setGroup(Flask.Constants.GRADLE_TASK_GROUP);
        setDescription("Creates an executable jar file, embedding all of its runtime dependencies and default JVM arguments");
        BasePluginConvention basePluginConvention = getProject().getConvention().getPlugin(BasePluginConvention.class);
        getDestinationDirectory().set(basePluginConvention.getLibsDirectory());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveExtension().convention("jar");
        launcherClassName = objects.property(String.class).convention(Flask.Constants.DEFAULT_LAUNCHER_NAME);
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
            if (!fileCopyDetails.isDirectory() && entryName.startsWith(Flask.Constants.LIBRARIES_FOLDER)) {
                Supplier<InputStream> streamSupplier = () -> Flask.read(fileCopyDetails.getFile(), false);
                Attributes attr = manifest.getEntries().computeIfAbsent(entryName, it -> new Attributes());
                md.reset();
                attr.putValue(Flask.ManifestAttributes.ENTRY_HASH,
                        Base64.getEncoder().encodeToString(Flask.computeDigest(streamSupplier, md, buffer)));
            }
            if (Flask.Constants.METADATA_FOLDER.equals(entryName)) return;
            ZipEntry zipEntry = zipEntryFactory.createZipEntry(entryName + (fileCopyDetails.isDirectory() ? "/" : ""));
            if(zipEntryFactory.isPreserveFileTimestamps) {
                zipEntry.setTime(fileCopyDetails.getLastModified());
            }
            if (fileCopyDetails.isDirectory()) {
                zoos.putNextEntry(zipEntry);
            } else {
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
                try (InputStream is = Flask.read(fileCopyDetails.getFile(), false)) {
                    zoos.putNextEntry(zipEntry);
                    Flask.write2Stream(is, zoos, buffer);
                }
            }
        }
    }

    @RequiredArgsConstructor
    private static class ZipEntryFactory {

        public final boolean isPreserveFileTimestamps;

        ZipEntry createZipEntry(String entryName) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            if(!isPreserveFileTimestamps) {
                zipEntry.setTime(Flask.Constants.ZIP_ENTRIES_DEFAULT_TIMESTAMP);
            }
            return zipEntry;
        }
    }

    @Override
    @Nonnull
    protected CopyAction createCopyAction() {
        File destination = getArchiveFile().get().getAsFile();
        return new CopyAction() {

            private final ZipEntryFactory zipEntryFactory = new ZipEntryFactory(isPreserveFileTimestamps());

            @Override
            @Nonnull
            @SneakyThrows
            public WorkResult execute(@Nonnull CopyActionProcessingStream copyActionProcessingStream) {
                Manifest manifest = new Manifest();
                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                mainAttributes.put(Attributes.Name.MAIN_CLASS, Flask.Constants.DEFAULT_LAUNCHER_NAME);
                mainAttributes.putValue(Flask.ManifestAttributes.LAUNCHER_CLASS, launcherClassName.get());
                mainAttributes.putValue(Flask.ManifestAttributes.PREMAIN_CLASS, Flask.Constants.DEFAULT_LAUNCHER_NAME);
                Optional.ofNullable(mainClassName.getOrNull()).ifPresent(it ->
                        mainAttributes.putValue(Flask.ManifestAttributes.APPLICATION_CLASS, it));
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[Flask.Constants.BUFFER_SIZE];
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
                 */
                File temporaryJar = new File(getTemporaryDir(), "premature.zip");
                try (ZipOutputStream zipOutputStream = new ZipOutputStream(Flask.write(temporaryJar, true))) {
                    StreamAction streamAction = new StreamAction(zipOutputStream, manifest, md, zipEntryFactory, buffer);
                    copyActionProcessingStream.process(streamAction);
                }

                try (ZipOutputStream zipOutputStream = new ZipOutputStream(Flask.write(destination, true));
                     ZipInputStream zipInputStream = new ZipInputStream(Flask.read(temporaryJar, true))) {
                    ZipEntry zipEntry = zipEntryFactory.createZipEntry(Flask.Constants.METADATA_FOLDER + '/');
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
                        zipEntry = zipEntryFactory.createZipEntry(Flask.Constants.JVM_ARGUMENT_FILE);
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
                        zipEntry = zipEntryFactory.createZipEntry(Flask.Constants.JAVA_AGENTS_FILE);
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                        zipOutputStream.putNextEntry(zipEntry);
                        Flask.storeProperties(javaAgentPropertyFile, zipOutputStream);
                    }

                    while (true) {
                        zipEntry = zipInputStream.getNextEntry();
                        if (zipEntry == null) break;
                        zipOutputStream.putNextEntry(zipEntry);
                        Flask.write2Stream(zipInputStream, zipOutputStream, buffer);
                    }
                    return () -> true;
                }
            }
        };
    }
}