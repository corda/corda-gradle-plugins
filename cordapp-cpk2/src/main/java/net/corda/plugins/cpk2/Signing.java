package net.corda.plugins.cpk2;

import net.corda.plugins.cpk2.signing.SigningOptions;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static net.corda.plugins.cpk2.signing.SigningOptions.SYSTEM_PROPERTY_PREFIX;

@SuppressWarnings("unused")
public class Signing {
    private final Property<Boolean> enabled;
    private final SigningOptions options;

    @Inject
    public Signing(@NotNull ObjectFactory objects, @NotNull ProviderFactory providers) {
        enabled = objects.property(Boolean.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + "enabled").orElse("true").map(Boolean::parseBoolean)
        );
        options = objects.newInstance(SigningOptions.class);
    }

    @Input
    @NotNull
    public Property<Boolean> getEnabled() {
        return enabled;
    }

    @Nested
    @NotNull
    public SigningOptions getOptions() {
        return options;
    }

    public void options(@NotNull Action<? super SigningOptions> action) {
        action.execute(options);
    }
}
