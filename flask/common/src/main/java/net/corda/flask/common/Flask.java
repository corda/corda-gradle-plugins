package net.corda.flask.common;

import lombok.SneakyThrows;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public class Flask {

    public static class Constants {
        public static final String DEFAULT_LAUNCHER_NAME = "net.corda.flask.launcher.Launcher";
        public static final String LIBRARIES_FOLDER = "LIB-INF";
        public static final String METADATA_FOLDER = "META-INF";
        public static final int BUFFER_SIZE = 0x10000;
    }

    public static class ManifestAttributes {
        public static final String APPLICATION_CLASS = "Application-Class";
        public static final String JVM_ARGS = "JVM-Args";
        public static final String JAVA_AGENTS = "Java-Agents";
        public static final String ENTRY_HASH = "MD5-Digest";
    }

    public static class JvmProperties {
        /**
         * If this property is set, its value will be appended to the jvm arguments
         * list of the spawned Java process
         */
        public static final String JVM_ARGS = "net.corda.flask.jvm.args";

        /**
         * This JVM property can be used to override the path to the flask cache folder
         */
        public static final String CACHE_DIR = "net.corda.flask.cache.dir";

        /**
         * This JVM property can be used to always wipe the library cache directory before program startup
         */
        public static final String WIPE_CACHE = "net.corda.flask.cache.wipe";
    }

    @SneakyThrows
    public static void computeSizeAndCrc32(
            ZipEntry zipEntry,
            InputStream inputStream,
            byte[] buffer) {
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

    @SneakyThrows
    public static void write2Stream(InputStream inputStream, OutputStream os,
                                    byte[] buffer) {
        while (true) {
            int read = inputStream.read(buffer);
            if (read < 0) break;
            os.write(buffer, 0, read);
        }
    }

    public static void write2Stream(InputStream inputStream, OutputStream os) {
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

    @SneakyThrows
    public static String computeMd5DigestString(Supplier<InputStream> streamSupplier) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return computeDigestString(streamSupplier, md);
    }

    @SneakyThrows
    public static String computeDigestString(Supplier<InputStream> streamSupplier, MessageDigest md) {
        try(InputStream stream = new DigestInputStream(streamSupplier.get(), md)) {
            byte[] buffer = new byte[Flask.Constants.BUFFER_SIZE];
            while(stream.read(buffer) >= 0) {}
        }
        return Flask.bytes2Hex(md.digest());
    }

    public static String bytes2Hex(byte[] bytes) {
        return String.format("%032x", new BigInteger(1, bytes));
    }
}
