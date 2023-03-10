package net.corda.plugins.cpk2;

import org.gradle.api.tasks.TaskInputs;
import org.jetbrains.annotations.NotNull;

final class CordappProperties {
    private CordappProperties() {
    }

    /**
     * Registers these {@link CordappExtension} properties as task inputs,
     * because Gradle cannot "see" their {@code @Input} annotations yet.
     */
    static void nested(@NotNull TaskInputs inputs, @NotNull String nestName, @NotNull CordappExtension cordapp) {
        inputs.property(nestName + ".targetPlatformVersion", cordapp.getTargetPlatformVersion());
        inputs.property(nestName + ".minimumPlatformVersion", cordapp.getMinimumPlatformVersion());
        CordappDataProperties.nested(inputs, nestName + ".contract", cordapp.getContract());
        CordappDataProperties.nested(inputs, nestName + ".workflow", cordapp.getWorkflow());
        SigningProperties.nested(inputs, nestName + ".signing", cordapp.getSigning());
        inputs.property(nestName + ".sealing", cordapp.getSealing());
        inputs.property(nestName + ".bndVersion", cordapp.getBndVersion());
        inputs.property(nestName + ".osgiVersion", cordapp.getOsgiVersion());
        inputs.property(nestName + ".jetbrainsAnnotationsVersion", cordapp.getJetbrainsAnnotationsVersion());
        inputs.property(nestName + ".hashAlgorithm", cordapp.getHashAlgorithm());
    }
}
