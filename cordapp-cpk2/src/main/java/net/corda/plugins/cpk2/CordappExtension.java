package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

import static net.corda.plugins.cpk2.CordappUtils.CORDAPP_SEALING_SYSTEM_PROPERTY_NAME;
import static net.corda.plugins.cpk2.CordappUtils.PLATFORM_VERSION_X;

/**
 * Top-level CorDapp attributes.
 */
public class CordappExtension {
    private final Property<Integer> targetPlatformVersion;
    private final Property<Integer> minimumPlatformVersion;
    private final CordappData contract;
    private final CordappData workflow;
    private final Signing signing;
    private final Property<Boolean> sealing;
    private final Property<String> bndVersion;
    private final Property<String> osgiVersion;
    private final Property<String> jetbrainsAnnotationsVersion;
    private final Property<String> hashAlgorithm;

    @Inject
    public CordappExtension(
        @NotNull ObjectFactory objects,
        @NotNull ProviderFactory providers,
        @Nullable String osgiVersion,
        @Nullable String bndVersion,
        @Nullable String jetbrainsAnnotationsVersion
    ) {
        targetPlatformVersion = objects.property(Integer.class);
        minimumPlatformVersion = objects.property(Integer.class).convention(PLATFORM_VERSION_X);
        contract = objects.newInstance(CordappData.class);
        workflow = objects.newInstance(CordappData.class);
        signing = objects.newInstance(Signing.class);
        sealing = objects.property(Boolean.class).convention(
            providers.systemProperty(CORDAPP_SEALING_SYSTEM_PROPERTY_NAME).orElse("true").map(Boolean::parseBoolean)
        );
        this.bndVersion = objects.property(String.class).convention(bndVersion);
        this.osgiVersion = objects.property(String.class).convention(osgiVersion);
        this.jetbrainsAnnotationsVersion = objects.property(String.class).convention(jetbrainsAnnotationsVersion);
        hashAlgorithm = objects.property(String.class).convention("SHA-256");
    }

    @Input
    @NotNull
    public Property<Integer> getTargetPlatformVersion() {
        return targetPlatformVersion;
    }

    @Input
    @NotNull
    public Property<Integer> getMinimumPlatformVersion() {
        return minimumPlatformVersion;
    }

    /**
     * @return CorDapp Contract distribution information.
     */
    @Nested
    @NotNull
    public CordappData getContract() {
        return contract;
    }

    /**
     * @return CorDapp Workflow (flows and services) distribution information.
     */
    @Nested
    @NotNull
    public CordappData getWorkflow() {
        return workflow;
    }

    /**
     * @return Optional parameters for ANT signJar tasks to sign CorDapps.
     */
    @Nested
    @NotNull
    public Signing getSigning() {
        return signing;
    }

    /**
     * @return Optional marker to seal all packages in the JAR.
     */
    @Input
    @NotNull
    public Property<Boolean> getSealing() {
        return sealing;
    }

    @Input
    @NotNull
    public Property<String> getBndVersion() {
        return bndVersion;
    }

    @Input
    @NotNull
    public Property<String> getOsgiVersion() {
        return osgiVersion;
    }

    @Input
    @NotNull
    public Property<String> getJetbrainsAnnotationsVersion() {
        return jetbrainsAnnotationsVersion;
    }

    /**
     * @return This property only provides the default value for {@link CPKDependenciesTask#getHashAlgorithm},
     * which is annotated with @Input. Hence this is not a task input itself.
     */
    @Input
    @NotNull
    public Property<String> getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void contract(@NotNull Action<? super CordappData> action) {
        action.execute(contract);
    }

    public void workflow(@NotNull Action<? super CordappData> action) {
        action.execute(workflow);
    }

    public void signing(@NotNull Action<? super Signing> action) {
        action.execute(signing);
    }

    public void targetPlatformVersion(@Nullable Integer value) {
        targetPlatformVersion.set(value);
    }

    public void minimumPlatformVersion(@Nullable Integer value) {
        minimumPlatformVersion.set(value);
    }
}
