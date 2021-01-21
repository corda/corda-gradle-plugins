package net.corda.flask.launcher;

import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import net.corda.flask.common.StringParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class Launcher {

    private static String CACHE_FOLDER_DEFAULT_NAME = "flask_cache";
    private static Logger log = LoggerFactory.getLogger(JarCache.class);

    static List<String> splitManifestArgumentString(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        CharacterIterator it = new StringCharacterIterator(s);
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
             if (escape) {
                escape = false;
                switch (c) {
                    case '"':
                        sb.append('"');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case ' ':
                        sb.append(' ');
                        break;
                    default:
                        throw new StringParseException(String.format("Unrecognized escape sequence '\\%c'", c));
                }
            } else if(c == ' ') {
                 result.add(sb.toString());
                 sb = new StringBuilder();
            } else if (c == '\\') {
                escape = true;
            } else {
                sb.append(c);
            }
        }
        if(sb.length() > 0) result.add(sb.toString());
        return result;
    }

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

    public static void main(String[] args) {
        System.exit(new Launcher().launch(args));
    }

    @SneakyThrows
    private int launch(String[] args) {
        Manifest manifest;
        Path currentJar = getCurrentJar();
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(currentJar))) {
            manifest = jarInputStream.getManifest();
        }
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
            RuntimeMXBean info = ManagementFactory.getRuntimeMXBean();
            if(!info.getInputArguments().isEmpty()) {
                log.trace("Forwarding jvm arguments from Flask JVM: [{}]",
                        info.getInputArguments().stream()
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.joining(", ")));
                builder.getJvmArgs().addAll(info.getInputArguments());
            }
            Optional.ofNullable(System.getProperty(Flask.JvmProperties.JVM_ARGS)).ifPresent(prop -> {
                List<String> jvmArgs = splitManifestArgumentString(prop);
                log.trace("Adding jvm arguments from {}: [{}]", Flask.JvmProperties.JVM_ARGS,
                        jvmArgs.stream()
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.joining(", ")));
                builder.getJvmArgs().addAll(jvmArgs);
            });
            String jvmArgsManifestString = manifest.getMainAttributes().getValue(Flask.ManifestAttributes.JVM_ARGS);
            if(jvmArgsManifestString != null) {
                List<String> jvmArgs = splitManifestArgumentString(jvmArgsManifestString);
                log.trace("Adding jvm arguments from jar manifest '{}' attribute: [{}]", Flask.ManifestAttributes.JVM_ARGS,
                        jvmArgs.stream()
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.joining(", ")));
                builder.getJvmArgs().addAll(jvmArgs);
            }
            String javaAgentsManifestString = manifest.getMainAttributes().getValue(Flask.ManifestAttributes.JAVA_AGENTS);
            if(javaAgentsManifestString != null) {
                for(String javaAgentString : splitManifestArgumentString(javaAgentsManifestString)) {
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
                    StringBuilder sb = new StringBuilder("-javaagent:" + agentJar.toString());
                    if(equalCharPosition > 0) {
                        String agentArguments = javaAgentString.substring(equalCharPosition + 1);
                        log.trace("Adding Java agent '{}' with arguments '{}'", agentJar.getFileName().toString(), agentArguments);
                        sb.append('=');
                        sb.append(agentArguments);
                    }
                    builder.getJvmArgs().add(sb.toString());
                }
            }
            for(Path jarPath : extractedLibraries.values()) {
                builder.getClasspath().add(jarPath.toString());
            }
            builder.getCliArgs().addAll(Arrays.asList(args));
            beforeChildJvmStart(builder);
            int returnCode = builder.exec();
            afterChildJvmExit(returnCode);
            cache.updateLibraries();
            return returnCode;
        }
    }

    protected void beforeChildJvmStart(JavaProcessBuilder builder) {}
    protected void afterChildJvmExit(int returnCode) {}
}
