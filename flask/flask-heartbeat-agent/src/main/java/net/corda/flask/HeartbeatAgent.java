package net.corda.flask;

import net.corda.flask.common.Flask;
import net.corda.flask.common.LockFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HeartbeatAgent {
    public static void premain(String agentArgs) {
        Path pidFile = Paths.get(System.getProperty(Flask.JvmProperties.PID_FILE));
        Thread t = new Thread(() -> {
            LockFile.acquire(pidFile, true);
            try {
                Files.delete(pidFile);
            } catch(Exception ex) {
                //Should deletion fail, we only log the stacktrace because killing the current process, which
                // is the most important action here, must not be avoided
                ex.printStackTrace();
            }
            System.exit(-1);
        });
        t.setDaemon(true);
        t.start();
    }
}
