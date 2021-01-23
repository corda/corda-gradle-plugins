package net.corda.gradle.flask;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import net.corda.flask.common.ManifestEscape;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FlaskJarTask extends AbstractArchiveTask {

    @Setter
    private Property<String> launcherClassName;

    @Input
    public Property<String> getLauncherClassName() {
        return launcherClassName;
    }

    @Setter
    private Property<String> mainClassName;

    @Input
    public Property<String> getMainClassName() {
        return mainClassName;
    }

    @Setter
    private ListProperty<String> jvmArgs;

    @Input
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    public void includeLibraries(FileCollection fileCollection) {
        into(Flask.Constants.LIBRARIES_FOLDER, (copySpec) -> copySpec.from(fileCollection));
    }

    @RequiredArgsConstructor
    private static class JavaAgent {
        final File jar;
        final String args;
    }

    private final List<JavaAgent> javaAgents;

    @InputFiles
    FileCollection getAgentJars() {
        File[] jars = javaAgents.stream().map(it -> it.jar).toArray(File[]::new);
        return getProject().files((Object[]) jars);
    }

    @Input
    List<String> getAgentArgs() {
        return javaAgents.stream().map(it -> it.args).collect(Collectors.toList());
    }

    public void javaAgent(File jar, String agentArgs) {
        javaAgents.add(new JavaAgent(jar, agentArgs));
    }

    public FlaskJarTask() {
        BasePluginConvention basePluginConvention = getProject().getConvention().getPlugin(BasePluginConvention.class);
        getDestinationDirectory().set(basePluginConvention.getLibsDir());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveExtension().convention("jar");
        ObjectFactory objects = getProject().getObjects();
        launcherClassName = objects.property(String.class).convention(Flask.Constants.DEFAULT_LAUNCHER_NAME);
        mainClassName = objects.property(String.class);
        jvmArgs = objects.listProperty(String.class).convention(new ArrayList<>());
        javaAgents = new ArrayList<>();
        from(getProject().tarTree(LauncherResource.instance), copySpec -> exclude(JarFile.MANIFEST_NAME));
    }

    @Input
    @SneakyThrows
    public String getLauncherArchiveHash() {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try(InputStream stream = new DigestInputStream(LauncherResource.instance.read(), md5)) {
            byte[] buffer = new byte[0x10000];
            while(stream.read(buffer) >= 0) {}
        }
        return String.format("%032x", new BigInteger(1, md5.digest()));
    }

    @RequiredArgsConstructor
    private static class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zoos;
        private final Manifest manifest;
        private final MessageDigest md;
        private final byte[] buffer;

        private final List<FileCopyDetailsInternal> fileCopyDetailsInternals = new ArrayList<>();

        @SneakyThrows
        public void write() {
            ZipEntry zipEntry = new ZipEntry(Flask.Constants.METADATA_FOLDER + '/');
            zoos.putNextEntry(zipEntry);
            zipEntry = new ZipEntry(JarFile.MANIFEST_NAME);
            zipEntry.setMethod(ZipEntry.DEFLATED);
            zoos.putNextEntry(zipEntry);
            manifest.write(zoos);
            zoos.closeEntry();
            for(FileCopyDetails fileCopyDetails : fileCopyDetailsInternals) {
                String entryName = fileCopyDetails.getRelativePath().toString();
                if(Objects.equals(Flask.Constants.METADATA_FOLDER, entryName)) continue;
                zipEntry = new ZipEntry(entryName);
                if(fileCopyDetails.isDirectory()) {
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
                    try(InputStream is = new FileInputStream(fileCopyDetails.getFile())) {
                        zoos.putNextEntry(zipEntry);
                        Flask.write2Stream(zoos, is, buffer);
                        zoos.closeEntry();
                    }
                }
            }
        }

        @Override
        @SneakyThrows
        public void processFile(FileCopyDetailsInternal fileCopyDetailsInternal) {
            String entryName = fileCopyDetailsInternal.getRelativePath().toString();
            if(!fileCopyDetailsInternal.isDirectory() && entryName.startsWith(Flask.Constants.LIBRARIES_FOLDER)) {
                md.reset();
                try(InputStream is = new DigestInputStream(new FileInputStream(fileCopyDetailsInternal.getFile()), md)) {
                    while(is.read(buffer) >= 0) {}
                }
                Attributes attr = manifest.getEntries().computeIfAbsent(entryName, it -> new Attributes());
                attr.putValue(Flask.ManifestAttributes.ENTRY_HASH,
                        String.format("%032x", new BigInteger(1, md.digest())));
            }
            fileCopyDetailsInternals.add(fileCopyDetailsInternal);
        }
    }

    @Override
    protected CopyAction createCopyAction() {
        File destination = getArchiveFile().get().getAsFile();
        return new CopyAction() {
            @Override
            @SneakyThrows
            public WorkResult execute(CopyActionProcessingStream copyActionProcessingStream) {
                try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destination))) {
                    Manifest manifest = new Manifest();
                    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, launcherClassName.get());
                    Optional.ofNullable(mainClassName.getOrNull()).ifPresent(it ->
                        manifest.getMainAttributes().putValue(Flask.ManifestAttributes.APPLICATION_CLASS, it));
                    Optional.ofNullable(jvmArgs.getOrNull())
                            .filter(it -> !it.isEmpty())
                            .ifPresent(it -> manifest.getMainAttributes().putValue(Flask.ManifestAttributes.JVM_ARGS,
                                    ManifestEscape.escapeStringList(it)));
                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    byte[] buffer = new byte[0x10000];
                    if(!javaAgents.isEmpty()) {
                        List<String> agentsStrings = javaAgents.stream().map(new Function<JavaAgent, String>() {
                            @Override
                            @SneakyThrows
                            public String apply(JavaAgent javaAgent) {
                                md5.reset();
                                try (InputStream stream = new DigestInputStream(new FileInputStream(javaAgent.jar), md5)) {
                                    while (stream.read(buffer) >= 0) {
                                    }
                                }
                                StringBuilder sb = new StringBuilder();
                                sb.append(String.format("%032x", new BigInteger(1, md5.digest())));
                                if (!javaAgent.args.isEmpty()) {
                                    sb.append('=');
                                    sb.append(javaAgent.args);
                                }
                                return sb.toString();
                            }
                        }).collect(Collectors.toList());
                        manifest.getMainAttributes().putValue(Flask.ManifestAttributes.JAVA_AGENTS, ManifestEscape.escapeStringList(agentsStrings));
                    }
                    StreamAction streamAction = new StreamAction(zipOutputStream, manifest, md5, buffer);
                    copyActionProcessingStream.process(streamAction);
                    streamAction.write();
                    return () -> true;
                }
            }
        };
    }
}