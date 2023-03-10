package net.corda.plugins.cpk2;

import org.gradle.api.tasks.TaskInputs;
import org.jetbrains.annotations.NotNull;

final class OsgiProperties {
    private OsgiProperties() {
    }

    /**
     * Registers these {@link OsgiExtension} properties as task inputs,
     * because Gradle cannot "see" their {@code @Input} annotations yet.
     */
    static void nested(@NotNull TaskInputs inputs, @NotNull String nestName, @NotNull OsgiExtension osgi) {
        inputs.property(nestName + ".autoExport", osgi.getAutoExport());
        inputs.property(nestName + ".exports", osgi.getExports());
        inputs.property(nestName + ".embeddedJars", osgi.getEmbeddedJars());
        inputs.property(nestName + ".applyImportPolicies", osgi.getApplyImportPolicies());
        inputs.property(nestName + ".imports", osgi.getImports());
        inputs.property(nestName + ".scanCordaClasses", osgi.getScanCordaClasses());
        inputs.property(nestName + ".symbolicName", osgi.getSymbolicName());
    }
}
