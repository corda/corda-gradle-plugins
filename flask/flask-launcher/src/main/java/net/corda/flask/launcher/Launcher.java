package net.corda.flask.launcher;

import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import net.corda.flask.common.LockFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
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

    private static final String CACHE_FOLDER_DEFAULT_NAME = "flask_cache";
    private static final Logger log = LoggerFactory.getLogger(JarCache.class);
    private static final Path currentJar = findCurrentJar();

    @SneakyThrows
    private static Path findCurrentJar() {
        String launcherClassName = Launcher.class.getName();
        URL url = Launcher.class.getClassLoader().getResource(launcherClassName.replace('.', '/') + ".class");
        if (url == null || !Objects.equals("jar", url.getProtocol()))
            throw new IllegalStateException(String.format("The class %s must be used inside a JAR file", launcherClassName));
        String path = url.getPath();
        URI jarUri = new URI(path.substring(0, path.indexOf('!')));
        return Paths.get(jarUri);
    }

    @SneakyThrows
    private static List<String> listOfStringFromPropertyFile(InputStream is) {
        Properties p = new Properties();
        p.load(is);
        return p.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> Integer.parseInt(entry.getKey().toString())))
                .map(it -> it.getValue().toString())
                .collect(Collectors.toList());
    }

    private static List<String> extractJvmArgsFromCliArgs(String args[], List<String> passThroughJvmArguments) {
        List<String> result = new ArrayList<>();
        int originalLength = passThroughJvmArguments.size();
        for(String arg : args) {
            if(arg.startsWith(Flask.Constants.CLI_JVM_PARAMETERS_PREFIX)) {
                passThroughJvmArguments.add(arg.substring(Flask.Constants.CLI_JVM_PARAMETERS_PREFIX.length()));
            } else {
                result.add(arg);
            }
        }
        if(log.isTraceEnabled()) {
            log.trace("Adding jvm arguments from command line: [{}]",
                    passThroughJvmArguments.stream()
                            .skip(originalLength)
                            .map(s -> "\"" + s + "\"")
                            .collect(Collectors.joining(", ")));
        }
        return result;
    }

    @SneakyThrows
    public static void main(String[] args) {
        Manifest manifest = new Manifest();
        List<String> cliArgs;
        List<String> jvmArgs = new ArrayList<>();
        List<String> javaAgents = null;
        try(ZipFile jar = new ZipFile(currentJar.toFile())) {
            ZipEntry manifestEntry = jar.getEntry(JarFile.MANIFEST_NAME);
            jar.getInputStream(manifestEntry);
            try (InputStream inputStream = jar.getInputStream(manifestEntry)) {
                manifest.read(inputStream);
            }
            ZipEntry jvmArgsEntry = jar.getEntry(Flask.Constants.JVM_ARGUMENT_FILE);
            if(jvmArgsEntry != null) {
                List<String> jvmArgumentsFromPropertyFile;
                try (InputStream inputStream = jar.getInputStream(jvmArgsEntry)) {
                    jvmArgumentsFromPropertyFile = listOfStringFromPropertyFile(inputStream);
                }
                if(log.isTraceEnabled()) {
                    log.trace("Adding jvm arguments from {} property file: [{}]", Flask.Constants.JVM_ARGUMENT_FILE,
                            jvmArgumentsFromPropertyFile.stream()
                                    .map(s -> "\"" + s + "\"")
                                    .collect(Collectors.joining(", ")));
                }
                jvmArgs.addAll(jvmArgumentsFromPropertyFile);
            }
            ZipEntry javaAgentsEntry = jar.getEntry(Flask.Constants.JAVA_AGENTS_FILE);
            if(javaAgentsEntry != null) {
                try (InputStream inputStream = jar.getInputStream(jar.getEntry(Flask.Constants.JAVA_AGENTS_FILE))) {
                    javaAgents = listOfStringFromPropertyFile(inputStream);
                }
            }
        }
        cliArgs = extractJvmArgsFromCliArgs(args, jvmArgs);
        String mainClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        Class<? extends Launcher> launcherClass = (Class<? extends Launcher>)
                Class.forName(mainClassName, true, ClassLoader.getSystemClassLoader());
        Constructor<? extends Launcher> ctor = launcherClass.getConstructor();
        System.exit(ctor.newInstance().launch(manifest, jvmArgs, javaAgents, cliArgs));
    }

    @SneakyThrows
    int launch(Manifest manifest, List<String> jvmArgs, List<String> javaAgents, List<String> args) {
        JarCache cache = new JarCache(CACHE_FOLDER_DEFAULT_NAME);
        if(Boolean.parseBoolean(System.getProperty(Flask.JvmProperties.WIPE_CACHE))) {
            cache.wipeLibDir();
        } else if(Files.exists(cache.getLibDir())) {
            cache.cleanLibDir();
        }
        try(LockFile lf = LockFile.acquire(cache.getLockFile(), true)) {
            Map<String, Path> extractedLibraries = cache.extract(manifest);
            JavaProcessBuilder builder = new JavaProcessBuilder();
            builder.setMainClassName(Optional.ofNullable(System.getProperty(Flask.JvmProperties.MAIN_CLASS))
                    .orElse(manifest.getMainAttributes().getValue(Flask.ManifestAttributes.APPLICATION_CLASS)));
            if(jvmArgs != null) {
                builder.getJvmArgs().addAll(jvmArgs);
            }
            if(javaAgents != null) {
                for(String javaAgentString : javaAgents) {
                    int equalCharPosition = javaAgentString.indexOf('=');
                    String hash;
                    if(equalCharPosition < 0) {
                        hash = javaAgentString;
                    } else {
                        hash = javaAgentString.substring(0, equalCharPosition);
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
            builder.getCliArgs().addAll(args);
            beforeChildJvmStart(builder);
            try(LockFile processLock = LockFile.acquire(cache.getPidFile(), false)) {
                builder.getProperties().put(Flask.JvmProperties.PID_FILE, cache.getPidFile());
                Path heartbeatAgentPath = extractedLibraries.get(
                        manifest.getMainAttributes().getValue(Flask.ManifestAttributes.HEARTBEAT_AGENT_HASH));
                builder.getJvmArgs().add("-javaagent:" + heartbeatAgentPath);
                int returnCode = builder.exec();
                afterChildJvmExit(returnCode);
                cache.touchLibraries();
                Files.delete(cache.getPidFile());
                return returnCode;
            }
        }
    }

    protected void beforeChildJvmStart(JavaProcessBuilder builder) {}
    protected void afterChildJvmExit(int returnCode) {}
}
