package net.corda.flask.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public class Flask {

    public static class Constants {
        public static final String DEFAULT_LAUNCHER_NAME = "net.corda.flask.launcher.Launcher";
        public static final String LIBRARIES_FOLDER = "LIB-INF";
        public static final String METADATA_FOLDER = "META-INF";
        public static final String JVM_ARGUMENT_FILE = METADATA_FOLDER + "/jvmArgs.xml";
        public static final String JAVA_AGENTS_FILE = METADATA_FOLDER + "/javaAgents.xml";
        public static final String CLI_JVM_PARAMETERS_PREFIX = "-flaskJvmArg=";
        public static final int BUFFER_SIZE = 0x10000;
        public static final String GRADLE_TASK_GROUP = "Flask";
        public static final String DEFAULT_KILL_TIMEOUT_MILLIS = "10000";

        /**
         * This value is used as a default file timestamp for all the zip entries when
         * <a href="https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/bundling/AbstractArchiveTask.html#isPreserveFileTimestamps--">AbstractArchiveTask.isPreserveFileTimestamps</a>
         * is true; its value is taken from Gradle's <a href="https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java#L42-L57">ZipCopyAction<a/>
         * for the reasons outlined there.
         */
        public static final long ZIP_ENTRIES_DEFAULT_TIMESTAMP =
                new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();
    }

    public static class ManifestAttributes {
        public static final String LAUNCHER_CLASS = "Launcher-Class";
        public static final String APPLICATION_CLASS = "Application-Class";
        public static final String PREMAIN_CLASS = "Premain-Class";
        public static final String ENTRY_HASH = "SHA-256-Digest";
        public static final String HEARTBEAT_AGENT_HASH = "Heartbeat-Agent-Hash";
    }

    public static class JvmProperties {
        /**
         * This property will contain the location in the file system of the pid file
         * of the parent process that will be used by the child as heartbeat
         * (the parent holds en exclusive lock on that file, the child dies as soon as it manages to
         * acquire a shared lock on it)
         */
        public static final String PID_FILE = "net.corda.flask.pid.file";

        /**
         * This JVM property can be used to override the path to the flask cache folder
         */
        public static final String CACHE_DIR = "net.corda.flask.cache.dir";

        /**
         * This JVM property can be used to always wipe the library cache directory before program startup
         */
        public static final String WIPE_CACHE = "net.corda.flask.cache.wipe";

        /**
         * This property will contain the name of the main class the child process Launcher will start
         */
        public static final String MAIN_CLASS = "net.corda.flask.main.class";

        /**
         * If this property is set to true, the embedded Java agents will not be started
         */
        public static final String NO_JAVA_AGENT = "net.corda.flask.no.java.agent";

        /**
         * This property will contain the amount of time the parent process will wait
         * for the child process termination before killing it forcibly
         */
        public static final String KILL_TIMEOUT_MILLIS = "net.corda.flask.kill.timeout.millis";
    }

    public static void computeSizeAndCrc32(
            ZipEntry zipEntry,
            InputStream inputStream,
            byte[] buffer) throws IOException {
        CRC32 crc32 = new CRC32();
        long sz = 0L;
        while (true) {
            int read = inputStream.read(buffer);
            if (read < 0) break;
            sz += read;
            crc32.update(buffer, 0, read);
        }
        zipEntry.setSize(sz);
        zipEntry.setCompressedSize(sz);
        zipEntry.setCrc(crc32.getValue());
    }

    public static void write2Stream(InputStream inputStream, OutputStream os,
                                    byte[] buffer) throws IOException {
        while (true) {
            int read = inputStream.read(buffer);
            if (read < 0) break;
            os.write(buffer, 0, read);
        }
    }

    public static void write2Stream(InputStream inputStream, OutputStream os) throws IOException {
        write2Stream(inputStream, os, new byte[Constants.BUFFER_SIZE]);
    }

    public static Optional<Map.Entry<String, String>> splitExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index == -1) {
            return Optional.empty();
        } else {
            return Optional.of(
                    new AbstractMap.SimpleEntry<>(fileName.substring(0, index), fileName.substring(index)));
        }
    }

    public static byte[] computeSHA256Digest(Supplier<InputStream> streamSupplier) throws IOException, NoSuchAlgorithmException {
        byte[] buffer = new byte[Constants.BUFFER_SIZE];
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return computeDigest(streamSupplier, md, buffer);
    }

    public static byte[] computeDigest(Supplier<InputStream> streamSupplier, MessageDigest md, byte[] buffer) throws IOException {
        try(InputStream stream = new DigestInputStream(streamSupplier.get(), md)) {
            while(stream.read(buffer) >= 0) {}
        }
        return md.digest();
    }

    public static String bytes2Hex(byte[] bytes) {
        return String.format("%032x", new BigInteger(1, bytes));
    }

    /**
     * Helper method to create an {@link InputStream} from a file without having to catch the possibly
     * thrown {@link IOException}, use {@link FileInputStream#FileInputStream(File)} if you need to catch it.
     * @param file the {@link File} to be opened
     * @return an open {@link InputStream} instance reading from the file
     */
    public static InputStream read(File file, boolean buffered) throws IOException {
        InputStream result = new FileInputStream(file);
        return buffered ? new BufferedInputStream(result) : result;
    }

    /**
     * Helper method to create an {@link OutputStream} from a file without having to catch the possibly
     * thrown {@link IOException}, use {@link FileOutputStream#FileOutputStream(File)} if you need to catch it.
     * @param file the {@link File} to be opened
     * @return an open {@link OutputStream} instance writing to the file
     */
    public static OutputStream write(File file, boolean buffered) throws IOException {
        OutputStream result = new FileOutputStream(file);
        return buffered ? new BufferedOutputStream(result) : result;
    }

    public static void storeProperties(Properties properties, OutputStream outputStream) throws IOException {
        properties.storeToXML(outputStream, null, StandardCharsets.UTF_8.name());
    }

    public static void loadProperties(Properties properties, InputStream inputStream) throws IOException {
        properties.loadFromXML(inputStream);
    }
}
