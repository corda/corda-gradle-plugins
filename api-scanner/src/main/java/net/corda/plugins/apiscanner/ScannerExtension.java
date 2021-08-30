package net.corda.plugins.apiscanner;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

@SuppressWarnings({"unused", "rawtypes", "WeakerAccess", "UnstableApiUsage"})
public class ScannerExtension {

    private boolean enabled = true;
    private final Property<Boolean> verbose;
    private final SetProperty<String> excludeClasses;
    private final MapProperty<String, List> excludeMethods;
    private final SetProperty<String> excludePackages;
    private final Property<String> targetClassifier;

    @Inject
    public ScannerExtension(@Nonnull ObjectFactory objects, String defaultClassifier) {
        verbose = objects.property(Boolean.class).convention(false);
        excludeClasses = objects.setProperty(String.class);
        excludePackages = objects.setProperty(String.class);
        excludeMethods = objects.mapProperty(String.class, List.class);
        targetClassifier = objects.property(String.class).convention(defaultClassifier);
    }

    public Property<Boolean> getVerbose() {
        return verbose;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SetProperty<String> getExcludeClasses() {
        return excludeClasses;
    }

    public MapProperty<String, ? extends List> getExcludeMethods() {
        return excludeMethods;
    }

    public SetProperty<String> getExcludePackages() {
        return excludePackages;
    }

    public Property<String> getTargetClassifier() {
        return targetClassifier;
    }
}
