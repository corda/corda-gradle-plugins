package net.corda.gradle.flask.test;

import net.corda.flask.launcher.JavaProcessBuilder;
import net.corda.flask.launcher.Launcher;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Collectors;

public class TestLauncher extends Launcher {
    @Override
    protected void beforeChildJvmStart(JavaProcessBuilder builder) {
        try {
            Path outputfile = Paths.get("testLauncher.properties");
            Properties prop = new Properties();
            prop.setProperty("mainClassName", builder.getMainClassName());
            prop.setProperty("args", builder.getCliArgs().stream().collect(Collectors.joining(" ")));
            try (Writer writer = Files.newBufferedWriter(outputfile)) {
                prop.store(writer, null);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}