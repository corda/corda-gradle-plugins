package net.corda.plugins.cpk;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;

final class Signer {
    private final Task task;

    Signer(@NotNull Task task) {
        this.task = task;
    }

    void sign(@NotNull Path jarFile, @NotNull Map<String, String> options) {
        try {
            task.getAnt().invokeMethod("signjar", options);
        } catch (RuntimeException e) {
            Logger logger = task.getLogger();
            // Not adding error message as it's always meaningless, logs with --INFO level contain more insights
            throw new InvalidUserDataException("Exception while signing " + jarFile.getFileName() + ", " +
                "ensure the 'cordapp.signing.options' entry contains correct keyStore configuration, " +
                "or disable signing by 'cordapp.signing.enabled false'. " +
                (logger.isInfoEnabled() || logger.isDebugEnabled()
                    ? "Search for 'ant:signjar' in log output."
                    : "Run with --info or --debug option and search for 'ant:signjar' in log output. "),
                e);
        }
    }
}
