package net.corda.flask.launcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.corda.flask.common.LockFile;

public class LockFileTestMain {

    public static void main(String[] args) throws IOException, InterruptedException {
        final Path lockfilePath = Paths.get(args[0]);
        final boolean shared = Boolean.parseBoolean(args[1]);
        final boolean keep = Boolean.parseBoolean(args[2]);
        try (LockFile ignored = LockFile.acquire(lockfilePath, shared)) {
            while (keep) {
                Thread.sleep(1000);
            }
        }
    }
}
