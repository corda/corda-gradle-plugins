package net.corda.plugins.cpk2;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.jetbrains.annotations.NotNull;

/**
 * Check whether task {@code target} exists in Gradle's {@link TaskExecutionGraph}.
 * If it does, and it also has a dependent task called {@code dependencyName}
 * and with type {@code dependencyType}, then AND {@code target}'s "enabled" status
 * with its dependency's "enabled" status.
 */
final class CopyEnabledAction implements Action<TaskExecutionGraph> {
    private final Task target;
    private final Class<? extends Task> dependencyType;
    private final String dependencyName;

    CopyEnabledAction(
        @NotNull Task target,
        @NotNull Class<? extends Task> dependencyType,
        @NotNull String dependencyName
    ) {
        this.target = target;
        this.dependencyType = dependencyType;
        this.dependencyName = dependencyName;
    }

    @Override
    public void execute(@NotNull TaskExecutionGraph graph) {
        if (graph.hasTask(target)) {
            graph.getDependencies(target).stream()
                .filter(dep -> dependencyName.equals(dep.getName()) && dependencyType.isInstance(dep))
                .forEach(dep -> target.setEnabled(target.getEnabled() && dep.getEnabled()));
        }
    }
}
