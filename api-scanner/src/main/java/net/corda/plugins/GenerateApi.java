package net.corda.plugins;

import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * This task provides backwards compatibility with earlier versions of
 * the Corda Gradle plugins. Projects should migrate to using the new
 * {@link net.corda.plugins.apiscanner.GenerateApi} task instead.
 */
@Deprecated
@SuppressWarnings("UnstableApiUsage")
public class GenerateApi extends net.corda.plugins.apiscanner.GenerateApi {
    @Inject
    public GenerateApi(
        @Nonnull ObjectFactory objects,
        @Nonnull ProjectLayout layout,
        @Nonnull ProviderFactory providers
    ) {
        super(objects, layout, providers);
    }
}
