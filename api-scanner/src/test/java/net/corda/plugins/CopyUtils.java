package net.corda.plugins;

import org.apache.commons.io.IOUtils;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CopyUtils {
    private CopyUtils() {}

    public static int copyResourceTo(String resourceName, File target) throws IOException {
        try (
            InputStream input = CopyUtils.class.getClassLoader().getResourceAsStream(resourceName);
            OutputStream output = new FileOutputStream(target.getAbsolutePath())
        ) {
            return IOUtils.copy(input, output);
        }
    }

    public static String toString(Path file) throws IOException {
        try (Reader input = Files.newBufferedReader(file)) {
            return IOUtils.toString(input);
        }
    }

    public static Path pathOf(TemporaryFolder folder, String... elements) {
        return Paths.get(folder.getRoot().getAbsolutePath(), elements);
    }
}
