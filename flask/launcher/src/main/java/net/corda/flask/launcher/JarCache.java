package net.corda.flask.launcher;

import lombok.Getter;
import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

class JarCache {
    private static Logger log = LoggerFactory.getLogger(JarCache.class);

    @SneakyThrows
    static void deletePath(Path path) {
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(new Consumer<Path>() {
            @Override
            @SneakyThrows
            public void accept(Path path) {
                Files.delete(path);
                log.trace("Deleted {}", path);
            }
        });
    }

    @SneakyThrows
    private static boolean validateCacheDirectory(Path candidate) {
        try {
            if (!Files.exists(candidate)) {
                Files.createDirectories(candidate);
                return true;
            } else if (!Files.isDirectory(candidate)) {
                log.debug("Cache directory '{}' discarded because it is not a directory", candidate.toString());
                return false;
            } else if (!Files.isWritable(candidate)) {
                log.debug("Cache directory '{}' discarded because it is not writable", candidate.toString());
                return false;
            } else {
                log.debug("Using cache directory '{}'", candidate.toString());
                return true;
            }
        } catch (Exception ioe) {
            log.debug(
                    String.format("Cache directory '%s' discarded: %s", candidate.toString(), ioe.getMessage()),
                    ioe
            );
            return false;
        }
    }

    @SneakyThrows
    private static Path computeCacheDirectory(String appName) {
        Stream<Optional<Path>> candidates;
        if (OS.isUnix) {
            candidates = Stream.of(
                    Optional.ofNullable(System.getProperty(Flask.JvmProperties.CACHE_DIR)).map(Paths::get),
                    Optional.ofNullable(System.getenv("XDG_CACHE_HOME")).map(prefix -> Paths.get(prefix, appName)),
                    Optional.ofNullable(System.getProperty("user.home")).map(prefix -> Paths.get(prefix, ".cache", appName)),
                    Optional.ofNullable(System.getProperty("java.io.tmpdir")).map(path -> Paths.get(path).resolve(appName)),
                    Optional.of(Paths.get("/tmp", appName))
            );
        } else if (OS.isMac) {
            candidates = Stream.of(
                    Optional.ofNullable(System.getProperty(Flask.JvmProperties.CACHE_DIR)).map(Paths::get),
                    Optional.ofNullable(System.getProperty("user.home"))
                            .map(prefix -> Paths.get(prefix, "Library", "Saved Application State", appName)),
                    Optional.of(Paths.get("/Library/Caches", appName)),
                    Optional.ofNullable(System.getProperty("java.io.tmpdir"))
                            .map(prefix -> Paths.get(prefix).resolve(appName)));
        } else if (OS.isWindows) {
            candidates = Stream.of(
                    Optional.ofNullable(System.getProperty(Flask.JvmProperties.CACHE_DIR)).map(Paths::get),
                    Optional.ofNullable(System.getenv())
                            .map(it -> it.get("LOCALAPPDATA"))
                            .map(prefix -> Paths.get(prefix, appName)),
                    Optional.ofNullable(System.getProperty("user.home"))
                            .map(prefix -> Paths.get(prefix, "AppData", "Local", appName)),
                    Optional.ofNullable(System.getProperty("user.home"))
                            .map(prefix -> Paths.get(prefix, "Local Settings", "Application Data", appName)),
                    Optional.ofNullable(System.getProperty("java.io.tmpdir"))
                            .map(prefix -> Paths.get(prefix).resolve(appName))
            );

        } else {
            candidates = Stream.of(
                    Optional.ofNullable(System.getProperty(Flask.JvmProperties.CACHE_DIR)).map(Paths::get),
                    Optional.ofNullable(System.getProperty("java.io.tmpdir")).map(path -> Paths.get(path).resolve(appName))
            );
        }
        return candidates
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(JarCache::validateCacheDirectory)
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException("Unable to find a usable cache directory"));
    }

    @Getter
    private final Path path;

    @Getter
    private final Path libDir;

    @Getter
    private final Path lockFile;

    private final Map<String, Path> extractedLibraries;


    public JarCache(String appName) {
        path = computeCacheDirectory(appName);
        libDir = path.resolve("lib");
        lockFile = path.resolve("flask.lock");
        extractedLibraries = new TreeMap<>();
    }

    @SneakyThrows
    public Map<String, Path> extract(Manifest manifest) {
        byte[] buffer = new byte[0x10000];
        ClassLoader cl = Launcher.class.getClassLoader();
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            String jarEntryName = entry.getKey();
            Attributes attributes = entry.getValue();
            if (jarEntryName.startsWith(Flask.Constants.LIBRARIES_FOLDER + '/') && attributes.getValue(Flask.ManifestAttributes.ENTRY_HASH) != null) {
                String jarName = jarEntryName.substring(jarEntryName.lastIndexOf('/') + 1);
                String hash = attributes.getValue(Flask.ManifestAttributes.ENTRY_HASH);
                Path destination = libDir.resolve(hash).resolve(jarName);
                extractedLibraries.put(hash, destination);
                if (!Files.exists(destination)) {
                    Files.createDirectories(destination.getParent());
                    InputStream is = cl.getResourceAsStream(jarEntryName);
                    log.debug("Extracting '{}' to '{}'", jarEntryName, destination);
                    if (is != null) {
                        try {
                            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(destination))) {
                                while (true) {
                                    int read = is.read(buffer);
                                    if (read < 0) break;
                                    os.write(buffer, 0, read);
                                }
                            }
                        } finally {
                            is.close();
                        }
                    } else {
                        throw new RuntimeException(String.format("Entry '%s' missing from flask jar", jarEntryName));
                    }
                }
            }
        }
        return Collections.unmodifiableMap(extractedLibraries);
    }

    @SneakyThrows
    void touchLibraries() {
        FileTime now = FileTime.from(Instant.now());
        for(Path jarPath : extractedLibraries.values()) {
            Files.setLastModifiedTime(jarPath, now);
        }
    }

    @SneakyThrows
    void cleanLibDir() {
        LockFile lf = LockFile.tryAcquire(lockFile, false);
        if (lf != null) {
            log.debug("Starting library cache cleanup");
            try {
                FileTime threshold = FileTime.from(Instant.now().minus(Duration.ofDays(7)));
                log.trace("Removing all files that haven't been touched since {}", threshold);
                Files.list(libDir).filter(Files::isDirectory).forEach(new Consumer<Path>() {
                    @Override
                    @SneakyThrows
                    public void accept(Path hashFolder) {
                        Optional<FileTime> mostRecent = Files.walk(hashFolder)
                                .filter(Files::isRegularFile)
                                .map(new Function<Path, FileTime>() {
                                    @Override
                                    @SneakyThrows
                                    public FileTime apply(Path path) {
                                        return Files.getLastModifiedTime(path);
                                    }
                                }).max(FileTime::compareTo);
                        if (mostRecent.map(ft -> ft.compareTo(threshold) < 0).orElse(false)) {
                            JarCache.deletePath(hashFolder);
                        }
                    }
                });
            } finally {
                lf.close();
            }
            log.debug("Finished library cache cleanup");
        }
    }

    void wipeLibDir() {
        LockFile lockFile = LockFile.tryAcquire(this.lockFile, false);
        if (lockFile != null) {
            try {
                deletePath(libDir);
            } finally {
                lockFile.close();
            }
        }
    }
}
