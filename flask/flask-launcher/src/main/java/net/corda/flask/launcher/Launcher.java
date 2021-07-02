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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Launcher {

    private static final String CACHE_FOLDER_DEFAULT_NAME = "flask_cache";
    private static final Logger log = LoggerFactory.getLogger(Launcher.class);
    private static final Path currentJar = findCurrentJar();

    @SneakyThrows
    private static Path findCurrentJar() {
        String launcherClassName = Launcher.class.getName();
        URL url = Launcher.class.getClassLoader().getResource(launcherClassName.replace('.', '/') + ".class");
        if (url == null || !"jar".equals(url.getProtocol()))
            throw new IllegalStateException(String.format("The class %s must be used inside a JAR file", launcherClassName));
        String path = url.getPath();
        URI jarUri = new URI(path.substring(0, path.indexOf('!')));
        return Paths.get(jarUri);
    }

    @SneakyThrows
    private static List<String> listOfStringFromPropertyFile(InputStream is) {
        Properties p = new Properties();
        Flask.loadProperties(p, is);
        return p.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> Integer.parseInt(entry.getKey().toString())))
                .map(it -> it.getValue().toString())
                .collect(Collectors.toList());
    }

    private static List<String> extractFlaskArgs(String[] args, List<String> passThroughJvmArguments) {
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
        List<String> jvmArgs = new ArrayList<>();
        List<String> javaAgents = null;
        try(ZipFile jar = new ZipFile(currentJar.toFile())) {
            ZipEntry manifestEntry = jar.getEntry(JarFile.MANIFEST_NAME);
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
                try (InputStream inputStream = jar.getInputStream(javaAgentsEntry)) {
                    javaAgents = listOfStringFromPropertyFile(inputStream);
                }
            }
        }
        List<String> cliArgs = extractFlaskArgs(args, jvmArgs);
        String mainClassName = manifest.getMainAttributes().getValue(Flask.ManifestAttributes.LAUNCHER_CLASS);
        @SuppressWarnings("unchecked")
        Class<? extends Launcher> launcherClass = (Class<? extends Launcher>)
                Class.forName(mainClassName, true, Launcher.class.getClassLoader());
        Constructor<? extends Launcher> ctor = launcherClass.getConstructor();
        System.exit(ctor.newInstance().launch(manifest, jvmArgs, javaAgents, cliArgs));
    }

    @SneakyThrows
    final int launch(Manifest manifest, List<String> jvmArgs, List<String> javaAgents, List<String> args) {
        JarCache cache = new JarCache(CACHE_FOLDER_DEFAULT_NAME);
        if(Boolean.getBoolean(Flask.JvmProperties.WIPE_CACHE)) {
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
            boolean disableJavaAgents = Boolean.getBoolean(Flask.JvmProperties.NO_JAVA_AGENT);
            if(javaAgents != null && !disableJavaAgents) {
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
            builder.getProperties().put(Flask.JvmProperties.PID_FILE, cache.getPidFile());
            Path heartbeatAgentPath = extractedLibraries.get(
                    manifest.getMainAttributes().getValue(Flask.ManifestAttributes.HEARTBEAT_AGENT_HASH));
            builder.getJvmArgs().add("-javaagent:" + heartbeatAgentPath);
            LockFile processLock = LockFile.acquire(cache.getPidFile(), false);
            Process process = builder.build().inheritIO().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if(process.isAlive()) {
                    process.destroy();
                    try {
                        long timeout = Long.parseLong(
                                System.getProperty(
                                        Flask.JvmProperties.KILL_TIMEOUT_MILLIS,
                                        Flask.Constants.DEFAULT_KILL_TIMEOUT_MILLIS));
                        process.waitFor(timeout, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    } finally {
                        if (process.isAlive()) {
                            process.destroyForcibly();
                        }
                        processLock.close();
                    }
                }
                try {
                    Files.delete(cache.getPidFile());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                cache.touchLibraries();
            }));
            int returnCode = process.waitFor();
            afterChildJvmExit(returnCode);
            return returnCode;
        }
    }

    protected void beforeChildJvmStart(JavaProcessBuilder builder) {}
    protected void afterChildJvmExit(int returnCode) {}
}
