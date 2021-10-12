package net.corda.plugins.apiscanner;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;

@SuppressWarnings({"unused", "WeakerAccess"})
public class ScannerExtension {

    private boolean enabled = true;
    private final Property<Boolean> verbose;
    private final SetProperty<String> excludeClasses;
    private final MapProperty<String, List> excludeMethods;
    private final SetProperty<String> excludePackages;
    private final Property<String> targetClassifier;

    @Inject
    public ScannerExtension(@Nonnull ObjectFactory objectFactory, String defaultClassifier) {
        verbose = objectFactory.property(Boolean.class).convention(false);
        excludeClasses = objectFactory.setProperty(String.class);
        excludePackages = objectFactory.setProperty(String.class);
        excludeMethods = objectFactory.mapProperty(String.class, List.class);
        targetClassifier = objectFactory.property(String.class).convention(defaultClassifier);
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
