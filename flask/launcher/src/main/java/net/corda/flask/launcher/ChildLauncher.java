package net.corda.flask.launcher;

import lombok.SneakyThrows;
import net.corda.flask.common.Flask;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChildLauncher {
    @SneakyThrows
    public static void main(String[] args) {
        Path pidFile = Paths.get(System.getProperty(Flask.JvmProperties.PID_FILE));
        Thread t = new Thread(() -> {
          LockFile.acquire(pidFile, true);
          System.exit(-1);
        });
        t.setDaemon(true);
        t.start();
        String mainClassName = System.getProperty(Flask.JvmProperties.MAIN_CLASS);
        Class<?> cls = Class.forName(mainClassName);
        Method mainMethod = cls.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }
}
