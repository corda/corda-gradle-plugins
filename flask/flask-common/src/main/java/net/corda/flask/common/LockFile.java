package net.corda.flask.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

@RequiredArgsConstructor
public class LockFile implements Closeable {

    private final FileLock lock;

    private static FileChannel openFileChannel(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return FileChannel.open(path, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
    }

    @SneakyThrows
    public static LockFile acquire(Path path, boolean shared) {
        FileChannel channel = openFileChannel(path);
        return new LockFile(channel.lock(0L, Long.MAX_VALUE, shared));
    }

    @SneakyThrows
    public static LockFile tryAcquire(Path path, boolean shared) {
        FileChannel channel = openFileChannel(path);
        FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, shared);
        return (lock != null) ? new LockFile(lock) : null;
    }

    @Override
    @SneakyThrows
    public void close() {
        lock.channel().close();
    }
}
