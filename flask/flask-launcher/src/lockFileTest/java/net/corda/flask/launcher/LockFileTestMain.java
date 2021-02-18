package net.corda.flask.launcher;

import lombok.SneakyThrows;

import java.nio.file.Path;
import java.nio.file.Paths;

import net.corda.flask.common.LockFile;

public class LockFileTestMain {

    @SneakyThrows
    public static void main(String[] args) {
        Path lockfilePath = Paths.get(args[0]);
        boolean shared = Boolean.parseBoolean(args[1]);
        boolean keep = Boolean.parseBoolean(args[2]);
        try (LockFile lockfile = LockFile.acquire(lockfilePath, shared)) {
            while (keep) {
                Thread.sleep(1000);
            }
        }
    }
}