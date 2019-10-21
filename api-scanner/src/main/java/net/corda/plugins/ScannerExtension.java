package net.corda.plugins;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@SuppressWarnings({"unused", "WeakerAccess"})
public class ScannerExtension {

    private boolean verbose;
    private boolean enabled = true;
    private List<String> excludeClasses = emptyList();
    private Map<String, List<String>> excludeMethods = emptyMap();
    private final Property<String> targetClassifier;

    @Inject
    public ScannerExtension(ObjectFactory objectFactory, String defaultClassifier) {
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

    public List<String> getExcludeClasses() {
        return excludeClasses;
    }

    public void setExcludeClasses(List<String> excludeClasses) {
        this.excludeClasses = excludeClasses;
    }

    public Map<String, List<String>> getExcludeMethods() {
        return excludeMethods;
    }

    public void setExcludeMethods(Map<String, List<String>> excludeMethods) {
        this.excludeMethods = excludeMethods;
    }

    public Property<String> getTargetClassifier() {
        return targetClassifier;
    }
}
