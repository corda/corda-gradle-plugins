package net.corda.flask.launcher;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LockFileTest {

    @TempDir
    public Path testDir;

    private final Path executablePath = Paths.get(System.getProperty("lockFileTest.executable.jar"));

    private static class LockFileTestMainArgs {
        final Path lockFilePath;
        final boolean shared;
        final boolean keep;

        LockFileTestMainArgs(Path lockFilePath, boolean shared, boolean keep) {
            this.lockFilePath = lockFilePath;
            this.shared = shared;
            this.keep = keep;
        }

        public List<String> getArgs() {
            return Arrays.asList(lockFilePath.toString(), Boolean.toString(shared), Boolean.toString(keep));
        }
    }

    private static void kill(Process p) throws InterruptedException {
        if (p != null && p.isAlive()) p.destroyForcibly().waitFor();
    }

    @Test
    public void testExclusiveLockHeldOnFile() throws Exception {
        Path lockFilePath = Files.createFile(testDir.resolve("file.lock"));
        // try to acquire an exclusive lock and check that the process returns immediately
        JavaProcessBuilder javaProcessBuilder = new JavaProcessBuilder();
        javaProcessBuilder.setExecutableJar(executablePath);
        javaProcessBuilder.setCliArgs(new LockFileTestMainArgs(lockFilePath, false, false).getArgs());
        Process process = javaProcessBuilder.build()
                .inheritIO()
                .start();
        Assertions.assertTrue(process.waitFor(1, TimeUnit.SECONDS));
        Assertions.assertEquals(0, process.exitValue());

        Process sharedLockProcess = null;
        Process anotherSharedLockProcess = null;
        Process exclusiveLockProcess = null;
        try {
            // try to acquire and keep a shared lock on the file and check that the process does not exit
            javaProcessBuilder.setCliArgs(new LockFileTestMainArgs(lockFilePath, true, true).getArgs());
            sharedLockProcess = javaProcessBuilder.build()
                    .inheritIO()
                    .start();
            Assertions.assertFalse(sharedLockProcess.waitFor(1000, TimeUnit.MILLISECONDS));

            // try to acquire another shared lock on the file and check that the process is able to terminate
            javaProcessBuilder.setCliArgs(new LockFileTestMainArgs(lockFilePath, true, false).getArgs());
            anotherSharedLockProcess = javaProcessBuilder.build()
                    .inheritIO()
                    .start();
            Assertions.assertTrue(anotherSharedLockProcess.waitFor(1, TimeUnit.SECONDS));

            // try to acquire an exclusive lock on the file and check that process hangs
            javaProcessBuilder.setCliArgs(new LockFileTestMainArgs(lockFilePath, false, false).getArgs());
            exclusiveLockProcess = javaProcessBuilder.build()
                    .inheritIO()
                    .start();
            Assertions.assertFalse(exclusiveLockProcess.waitFor(1, TimeUnit.SECONDS));
            // kill the process holding the shared lock and check that the process holding the exclusive lock terminates
            sharedLockProcess.destroyForcibly().waitFor();
            Assertions.assertTrue(exclusiveLockProcess.waitFor(1, TimeUnit.SECONDS));
            Assertions.assertEquals(0, exclusiveLockProcess.exitValue());
        } finally {
            kill(sharedLockProcess);
            kill(anotherSharedLockProcess);
            kill(exclusiveLockProcess);
        }
    }
}
