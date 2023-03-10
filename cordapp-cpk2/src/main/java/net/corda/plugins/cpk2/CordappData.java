package net.corda.plugins.cpk2;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

public class CordappData {
    private final Property<String> name;
    private final Property<Integer> versionId;
    private final Property<String> vendor;
    private final Property<String> licence;

    @Inject
    public CordappData(@NotNull ObjectFactory objects) {
        name = objects.property(String.class);
        versionId = objects.property(Integer.class);
        vendor = objects.property(String.class);
        licence = objects.property(String.class);
    }

    @Optional
    @Input
    public Property<String> getName() {
        return name;
    }

    @Optional
    @Input
    public Property<Integer> getVersionId() {
        return versionId;
    }

    @Optional
    @Input
    public Property<String> getVendor() {
        return vendor;
    }

    @Optional
    @Input
    public Property<String> getLicence() {
        return licence;
    }

    boolean isEmpty() {
        return (!name.isPresent() && !versionId.isPresent() && !vendor.isPresent() && !licence.isPresent());
    }

    public void name(@Nullable String value) {
        name.set(value);
    }

    public void versionId(@Nullable Integer value) {
        versionId.set(value);
    }

    public void vendor(@Nullable String value) {
        vendor.set(value);
    }

    public void licence(@Nullable String value) {
        licence.set(value);
    }
}
