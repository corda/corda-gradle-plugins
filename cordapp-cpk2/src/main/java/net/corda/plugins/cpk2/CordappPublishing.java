package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Ensure that we add the {@link PublishAfterEvaluationHandler}
 * to the root project exactly once, regardless of how
 * many times this {@link Action} is invoked.
 */
final class CordappPublishing implements Action<AppliedPlugin> {
    private final Project rootProject;

    CordappPublishing(@NotNull Project rootProject) {
        this.rootProject = rootProject;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean setExtraProperty(@NotNull String key) {
        final ExtraPropertiesExtension rootProperties = rootProject.getExtensions().getExtraProperties();
        synchronized(rootProject) {
            if (rootProperties.has(key)) {
                return false;
            } else {
                rootProperties.set(key, new Object());
                return true;
            }
        }
    }

    @Override
    public void execute(@NotNull AppliedPlugin plugin) {
        if (setExtraProperty("_net_corda_cordapp_cpk2_publish_")) {
            rootProject.getGradle().projectsEvaluated(new PublishAfterEvaluationHandler(rootProject));
        }
    }
}
