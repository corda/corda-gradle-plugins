package net.corda.plugins;

import org.apache.commons.io.IOUtils;

import java.io.*;

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

    public static String toString(File file) throws IOException {
        try (Reader input = new FileReader(file)) {
            return IOUtils.toString(input);
        }
    }
}
