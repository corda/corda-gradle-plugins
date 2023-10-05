package net.corda.plugins.cpk2;

import org.gradle.api.tasks.TaskInputs;
import org.jetbrains.annotations.NotNull;

final class CordappDataProperties {
    private CordappDataProperties() {
    }

    /**
     * Registers these {@link CordappData} properties as task inputs,
     * because Gradle cannot "see" their {@code @Input} annotations yet.
     */
    static void nested(@NotNull TaskInputs inputs, @NotNull String nestName, @NotNull CordappData data) {
        inputs.property(nestName + ".name", data.getName()).optional(true);
        inputs.property(nestName + ".versionId", data.getVersionId()).optional(true);
        inputs.property(nestName + ".vendor", data.getVendor()).optional(true);
        inputs.property(nestName + ".licence", data.getLicence()).optional(true);
        inputs.property(nestName + ".cordappName", data.getCordappName()).optional(true);
    }
}
