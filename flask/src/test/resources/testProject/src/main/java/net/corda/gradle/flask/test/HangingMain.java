package net.corda.gradle.flask.test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

public class HangingMain {
    public static void main(String[] args) throws Exception {
        Path file = Paths.get("shutdown-hook-executed");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.delete(file);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }));
        Files.createFile(file);
        while(true) Thread.sleep(1000);
    }
}