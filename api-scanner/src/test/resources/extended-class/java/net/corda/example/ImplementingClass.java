package net.corda.example;

import java.io.*;

public class ImplementingClass implements AutoCloseable, Closeable {
    @Override
    public void close() throws IOException {
    }
}
