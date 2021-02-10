package net.corda.gradle.flask;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FlaskJarTask extends AbstractArchiveTask {

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

    public void includeLibraries(Object files) {
        into(Flask.Constants.LIBRARIES_FOLDER, (copySpec) -> copySpec.from(files));
    }

    private static class JavaAgent {
        File jar;
        String args;
    }

    private final List<JavaAgent> javaAgents;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getAgentJars() {
        File[] jars = javaAgents.stream().map(it -> it.jar).toArray(File[]::new);
        return getProject().files((Object[]) jars);
    }

    @Input
    List<String> getAgentArgs() {
        return javaAgents.stream().map(it -> it.args).collect(Collectors.toList());
    }

    public void javaAgent(Action<JavaAgent> action) {
        JavaAgent agent = new JavaAgent();
        action.execute(agent);
        if (agent.jar == null) throw new GradleException("No jar file specified for Java agent");
        javaAgents.add(agent);
    }

    @Inject
    public FlaskJarTask(ObjectFactory objects) {
        BasePluginConvention basePluginConvention = getProject().getConvention().getPlugin(BasePluginConvention.class);
        getDestinationDirectory().set(basePluginConvention.getLibsDir());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveExtension().convention("jar");
        launcherClassName = objects.property(String.class).convention(Flask.Constants.DEFAULT_LAUNCHER_NAME);
        mainClassName = objects.property(String.class);
        jvmArgs = objects.listProperty(String.class);
        javaAgents = new ArrayList<>();
        from(getProject().tarTree(LauncherResource.instance), copySpec -> exclude(JarFile.MANIFEST_NAME));

        Provider<File> heartbeatJarProvider = getProject().provider(() -> {
            File dest = new File(FlaskJarTask.this.getTemporaryDir(), HeartbeatAgentResource.instance.getDisplayName());
            try (OutputStream os = new FileOutputStream(dest); InputStream is = HeartbeatAgentResource.instance.read()) {
                Flask.write2Stream(is, os);
            }
            return dest;
        });
        includeLibraries(heartbeatJarProvider);
    }

    @Input
    @SneakyThrows
    public String getLauncherArchiveHash() {
        return Flask.bytes2Hex(Flask.computeSHA256Digest(LauncherResource.instance::read));
    }

    @Input
    @SneakyThrows
    public String getHeartbeatAgentHash() {
        return Flask.bytes2Hex(Flask.computeSHA256Digest(HeartbeatAgentResource.instance::read));
    }

    @RequiredArgsConstructor
    private static class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zoos;
        private final Manifest manifest;
        private final MessageDigest md;
        private final byte[] buffer;

        @Override
        @SneakyThrows
        public void processFile(FileCopyDetailsInternal fileCopyDetails) {
            String entryName = fileCopyDetails.getRelativePath().toString();
            if (!fileCopyDetails.isDirectory() && entryName.startsWith(Flask.Constants.LIBRARIES_FOLDER)) {
                Supplier<InputStream> streamSupplier = new Supplier<InputStream>() {
                    @Override
                    @SneakyThrows
                    public InputStream get() {
                        return new FileInputStream(fileCopyDetails.getFile());
                    }
                };
                Attributes attr = manifest.getEntries().computeIfAbsent(entryName, it -> new Attributes());
                md.reset();
                attr.putValue(Flask.ManifestAttributes.ENTRY_HASH,
                        Base64.getEncoder().encodeToString(Flask.computeDigest(streamSupplier, md)));
            }
            if (Objects.equals(Flask.Constants.METADATA_FOLDER, entryName)) return;
            ZipEntry zipEntry = new ZipEntry(entryName);
            if (fileCopyDetails.isDirectory()) {
                zipEntry = new ZipEntry(entryName + '/');
                zoos.putNextEntry(zipEntry);
            } else {
                boolean compressed = Flask.splitExtension(fileCopyDetails.getSourceName())
                        .map(entry -> Objects.equals(".jar", entry.getValue()))
                        .orElse(false);
                if (!compressed) {
                    zipEntry.setMethod(ZipEntry.DEFLATED);
                } else {
                    try (InputStream is = new FileInputStream(fileCopyDetails.getFile())) {
                        Flask.computeSizeAndCrc32(zipEntry, is, buffer);
                    }
                    zipEntry.setMethod(ZipEntry.STORED);
                }
                try (InputStream is = new FileInputStream(fileCopyDetails.getFile())) {
                    zoos.putNextEntry(zipEntry);
                    Flask.write2Stream(is, zoos, buffer);
                }
            }
        }
    }

    @Override
    protected CopyAction createCopyAction() {
        File destination = getArchiveFile().get().getAsFile();
        return new CopyAction() {
            @Override
            @SneakyThrows
            public WorkResult execute(CopyActionProcessingStream copyActionProcessingStream) {
                Manifest manifest = new Manifest();
                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                mainAttributes.put(Attributes.Name.MAIN_CLASS, Flask.Constants.DEFAULT_LAUNCHER_NAME);
                mainAttributes.putValue(Flask.ManifestAttributes.LAUNCHER_CLASS, launcherClassName.get());
                mainAttributes.putValue(Flask.ManifestAttributes.PREMAIN_CLASS, Flask.Constants.DEFAULT_LAUNCHER_NAME);
                Optional.ofNullable(mainClassName.getOrNull()).ifPresent(it ->
                        mainAttributes.putValue(Flask.ManifestAttributes.APPLICATION_CLASS, it));
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                mainAttributes.putValue(Flask.ManifestAttributes.HEARTBEAT_AGENT_HASH,
                Flask.bytes2Hex(Flask.computeDigest(HeartbeatAgentResource.instance::read, md)));
                byte[] buffer = new byte[Flask.Constants.BUFFER_SIZE];

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
                try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(temporaryJar))) {
                    StreamAction streamAction = new StreamAction(zipOutputStream, manifest, md, buffer);
                    copyActionProcessingStream.process(streamAction);
                }

                try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destination));
                     ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(temporaryJar))) {
                    ZipEntry zipEntry = new ZipEntry(Flask.Constants.METADATA_FOLDER + '/');
                    zipOutputStream.putNextEntry(zipEntry);
                    zipEntry = new ZipEntry(JarFile.MANIFEST_NAME);
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
                        zipEntry = new ZipEntry(Flask.Constants.JVM_ARGUMENT_FILE);
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                        zipOutputStream.putNextEntry(zipEntry);
                        jvmArgsPropertyFile.store(zipOutputStream, null);
                    }

                    if (!javaAgents.isEmpty()) {
                        Properties javaAgentPropertyFile = new Properties();
                        for (int i = 0; i < javaAgents.size(); i++) {
                            md.reset();
                            JavaAgent javaAgent = javaAgents.get(i);
                            Supplier<InputStream> streamSupplier = new Supplier<InputStream>() {
                                @Override
                                @SneakyThrows
                                public InputStream get() {
                                    return new FileInputStream(javaAgent.jar);
                                }
                            };
                            StringBuilder sb = new StringBuilder();
                            sb.append(Flask.bytes2Hex(Flask.computeDigest(streamSupplier, md)));
                            if (!javaAgent.args.isEmpty()) {
                                sb.append('=');
                                sb.append(javaAgent.args);
                            }
                            javaAgentPropertyFile.setProperty(Integer.toString(i), sb.toString());
                        }
                        zipEntry = new ZipEntry(Flask.Constants.JAVA_AGENTS_FILE);
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                        zipOutputStream.putNextEntry(zipEntry);
                        javaAgentPropertyFile.store(zipOutputStream, null);
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