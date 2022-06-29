package org.testing.compress;

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class ExampleZip implements Closeable {
    private final ZipArchiveInputStream zip;

    public ExampleZip(InputStream input) {
        zip = new ZipArchiveInputStream(input);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
