package net.corda.plugins.cpk2;

import net.corda.plugins.cpk2.signing.SigningOptionsProperties;
import org.gradle.api.tasks.TaskInputs;
import org.jetbrains.annotations.NotNull;

public final class SigningProperties {
    private SigningProperties() {
    }

    /**
     * Registers these {@link Signing} properties as task inputs,
     * because Gradle cannot "see" their {@code @Input} annotations yet.
     * @param inputs The {@link TaskInputs} to receive the new properties.
     * @param nestName The common prefix for all new property names.
     * @param signing The {@link Signing} for this task.
     */
    public static void nested(@NotNull TaskInputs inputs, @NotNull String nestName, @NotNull Signing signing) {
        inputs.property(nestName + ".enabled", signing.getEnabled());
        SigningOptionsProperties.nested(inputs, nestName + ".options", signing.getOptions());
    }
}
