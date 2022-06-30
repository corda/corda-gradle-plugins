package com.example.embeddable;

import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.StringJoiner;

public final class LogOutput {
    private static final Logger LOG = LoggerFactory.getLogger(LogOutput.class);

    private LogOutput()  {}

    public static void log(byte[] data) {
        LOG.debug("Received data: {}", data);
        try (InputStream input = new ByteArrayInputStream(data)) {
            IOUtils.copyLarge(input, System.out);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    public static String toHexString(byte[] data) {
        StringJoiner joiner = new StringJoiner("");
        for (byte b: data) {
            joiner.add(String.format("%02x", b));
        }
        return joiner.toString();
    }
}
