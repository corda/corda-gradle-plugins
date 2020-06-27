package net.corda.plugins.apiscanner;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

@SuppressWarnings({"unused", "WeakerAccess", "UnstableApiUsage"})
public class ScannerExtension {

    private boolean verbose;
    private boolean enabled = true;
    private final SetProperty<String> excludeClasses;
    private Map<String, List<String>> excludeMethods = emptyMap();
    private final SetProperty<String> excludePackages;
    private final Property<String> targetClassifier;

    @Inject
    public ScannerExtension(ObjectFactory objectFactory, String defaultClassifier) {
        excludeClasses = objectFactory.setProperty(String.class);
        excludePackages = objectFactory.setProperty(String.class);
        targetClassifier = objectFactory.property(String.class).convention(defaultClassifier);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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

    public Map<String, List<String>> getExcludeMethods() {
        return excludeMethods;
    }

    public void setExcludeMethods(Map<String, List<String>> excludeMethods) {
        this.excludeMethods = excludeMethods;
    }

    public SetProperty<String> getExcludePackages() {
        return excludePackages;
    }

    public Property<String> getTargetClassifier() {
        return targetClassifier;
    }
}
