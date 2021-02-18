package net.corda.flask.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
public class LockFile implements Closeable {

    private final FileLock lock;

    private static RandomAccessFile createLockFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) Files.createFile(path);
        return new RandomAccessFile(path.toFile(), "rw");
    }

    @SneakyThrows
    public static LockFile acquire(Path path, boolean shared) {
        RandomAccessFile raf = createLockFile(path);
        return new LockFile(raf.getChannel().lock(0L, Long.MAX_VALUE, shared));
    }

    @SneakyThrows
    public static LockFile tryAcquire(Path path, boolean shared) {
        RandomAccessFile raf = createLockFile(path);
        FileLock lock = raf.getChannel().tryLock(0L, Long.MAX_VALUE, shared);
        if(lock != null)
            return new LockFile(lock);
        else return null;
    }

    @Override
    @SneakyThrows
    public void close() {
        lock.release();
    }
}
