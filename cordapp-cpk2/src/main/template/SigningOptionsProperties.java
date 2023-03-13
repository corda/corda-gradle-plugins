package @root_package@.signing;

import org.gradle.api.tasks.TaskInputs;
import org.jetbrains.annotations.NotNull;

import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

/**
 * !!! GENERATED FILE - DO NOT EDIT !!!
 * See cordapp-cpk2/src/main/template/SigningOptionsProperties.java instead.
 * <p>
 * Registers the {@link SigningOptions} properties as task inputs,
 * because Gradle cannot "see" the {@code @Input} annotations yet.
 */
public final class SigningOptionsProperties {
    private SigningOptionsProperties() {
    }

    /**
     * Registers the {@link SigningOptions} properties as task inputs,
     * because Gradle cannot "see" the {@code @Input} annotations yet.
     * @param inputs The {@link TaskInputs} to receive the new properties.
     * @param nestName The common prefix for all new property names.
     * @param options The {@link SigningOptions} for this task.
     */
    public static void nested(@NotNull TaskInputs inputs, @NotNull String nestName, @NotNull SigningOptions options) {
        inputs.property(nestName + ".alias", options.getAlias());
        inputs.property(nestName + ".keyStore", options.getKeyStore()).optional(true);
        inputs.property(nestName + ".signatureFileName", options.getSignatureFileName());
        inputs.property(nestName + ".strict", options.getStrict());
        inputs.property(nestName + ".internalSF", options.getInternalSF());
        inputs.property(nestName + ".sectionsOnly", options.getSectionsOnly());
        inputs.property(nestName + ".lazy", options.getLazy());
        inputs.property(nestName + ".preserveLastModified", options.getPreserveLastModified());
        inputs.property(nestName + ".tsaCert", options.getTsaCert()).optional(true);
        inputs.property(nestName + ".tsaUrl", options.getTsaUrl()).optional(true);
        inputs.property(nestName + ".tsaProxyHost", options.getTsaProxyHost()).optional(true);
        inputs.property(nestName + ".tsaProxyPort", options.getTsaProxyPort()).optional(true);
        inputs.file(options.getExecutable()).withPropertyName(nestName + ".executable")
            .withPathSensitivity(RELATIVE)
            .optional();
        inputs.property(nestName + ".force", options.getForce());
        inputs.property(nestName + ".signatureAlgorithm", options.getSignatureAlgorithm()).optional(true);
        inputs.property(nestName + ".digestAlgorithm", options.getDigestAlgorithm()).optional(true);
        inputs.property(nestName + ".tsaDigestAlgorithm", options.getTsaDigestAlgorithm()).optional(true);
    }
}
