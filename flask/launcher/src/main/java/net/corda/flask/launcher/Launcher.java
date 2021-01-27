package net.corda.flask.launcher;

import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import net.corda.flask.common.ManifestEscape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Launcher {

    private static String CACHE_FOLDER_DEFAULT_NAME = "flask_cache";
    private static Logger log = LoggerFactory.getLogger(JarCache.class);

    @SneakyThrows
    private static Path getCurrentJar() {
        String launcherClassName = Launcher.class.getName();
        URL url = Launcher.class.getClassLoader().getResource(launcherClassName.replace('.', '/') + ".class");
        if (url == null || !Objects.equals("jar", url.getProtocol()))
            throw new IllegalStateException(String.format("The class %s must be used inside a JAR file", launcherClassName));
        String path = url.getPath();
        URI jarUri = new URI(path.substring(0, path.indexOf('!')));
        return Paths.get(jarUri);
    }

    @SneakyThrows
    public static void main(String[] args) {
        Path currentJar = getCurrentJar();
        ZipFile jar = new ZipFile(currentJar.toFile());
        ZipEntry manifestEntry = jar.getEntry(JarFile.MANIFEST_NAME);
        jar.getInputStream(manifestEntry);
        Manifest manifest = new Manifest();
        try (InputStream inputStream = jar.getInputStream(manifestEntry)) {
            manifest.read(inputStream);
        }
        String mainClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        Class<? extends Launcher> launcherClass = (Class<? extends Launcher>) Class.forName(mainClassName);
        Constructor<? extends Launcher> ctor = launcherClass.getConstructor();
        System.exit(ctor.newInstance().launch(manifest, args));
    }

    @SneakyThrows
    int launch(Manifest manifest, String[] args) {
        JarCache cache = new JarCache(CACHE_FOLDER_DEFAULT_NAME);
        if(Boolean.parseBoolean(System.getProperty(Flask.JvmProperties.WIPE_CACHE))) {
            cache.wipeLibDir();
        } else if(Files.exists(cache.getLibDir())) {
            cache.cleanLibDir();
        }
        try(LockFile lf = LockFile.acquire(cache.getLockFile(), true)) {
            Map<String, Path> extractedLibraries = cache.extract(manifest);
            JavaProcessBuilder builder = new JavaProcessBuilder();
            builder.setMainClassName(manifest.getMainAttributes().getValue(Flask.ManifestAttributes.APPLICATION_CLASS));
            Optional.ofNullable(System.getProperty(Flask.JvmProperties.JVM_ARGS)).ifPresent(prop -> {
                List<String> jvmArgs = ManifestEscape.splitManifestStringList(prop);
                log.trace("Adding jvm arguments from {}: [{}]", Flask.JvmProperties.JVM_ARGS,
                        jvmArgs.stream()
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.joining(", ")));
                builder.getJvmArgs().addAll(jvmArgs);
            });
            String jvmArgsManifestString = manifest.getMainAttributes().getValue(Flask.ManifestAttributes.JVM_ARGS);
            if(jvmArgsManifestString != null) {
                List<String> jvmArgs = ManifestEscape.splitManifestStringList(jvmArgsManifestString);
                log.trace("Adding jvm arguments from jar manifest '{}' attribute: [{}]", Flask.ManifestAttributes.JVM_ARGS,
                        jvmArgs.stream()
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.joining(", ")));
                builder.getJvmArgs().addAll(jvmArgs);
            }
            String javaAgentsManifestString = manifest.getMainAttributes().getValue(Flask.ManifestAttributes.JAVA_AGENTS);
            if(javaAgentsManifestString != null) {
                for(String javaAgentString : ManifestEscape.splitManifestStringList(javaAgentsManifestString)) {
                    int equalCharPosition = javaAgentString.indexOf('=');
                    String hash;
                    if(equalCharPosition < 0) {
                        hash = javaAgentsManifestString;
                    } else {
                        hash = javaAgentsManifestString.substring(0, equalCharPosition);
                    }
                    Path agentJar = Optional.ofNullable(extractedLibraries.get(hash))
                        .orElseThrow(() -> new IllegalStateException(String.format(
                            "Java agent jar with hash '%s' not found Flask cache", hash)));
                    String agentArguments = null;
                    if(equalCharPosition > 0) {
                        agentArguments = javaAgentString.substring(equalCharPosition + 1);
                        log.trace("Adding Java agent '{}' with arguments '{}'", agentJar.getFileName().toString(), agentArguments);
                    } else {
                        log.trace("Adding Java agent '{}'", agentJar.getFileName().toString());
                    }
                    builder.getJavaAgents().add(new JavaProcessBuilder.JavaAgent(agentJar, agentArguments));
                }
            }
            for(Path jarPath : extractedLibraries.values()) {
                builder.getClasspath().add(jarPath.toString());
            }
            builder.getCliArgs().addAll(Arrays.asList(args));
            beforeChildJvmStart(builder);
            int returnCode = builder.exec();
            afterChildJvmExit(returnCode);
            cache.touchLibraries();
            return returnCode;
        }
    }

    protected void beforeChildJvmStart(JavaProcessBuilder builder) {}
    protected void afterChildJvmExit(int returnCode) {}
}
