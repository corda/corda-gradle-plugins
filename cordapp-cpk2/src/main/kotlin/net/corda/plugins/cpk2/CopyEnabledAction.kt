@file:JvmName("CopyEnabledUtils")
package net.corda.plugins.cpk2

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.tasks.bundling.Jar

fun copyJarEnabledTo(target: Task): Action<TaskExecutionGraph>
        = CopyEnabledAction(target, Jar::class.java, JAR_TASK_NAME)
fun copyCpkEnabledTo(target: Task): Action<TaskExecutionGraph>
        = CopyEnabledAction(target, PackagingTask::class.java, CPK_TASK_NAME)

/**
 * Check whether task [target] exists in Gradle's [TaskExecutionGraph].
 * If it does, and it also has a dependent task called [dependencyName]
 * and with type [dependencyType], then AND [target]'s "enabled" status
 * with its dependency's "enabled" status.
 */
private class CopyEnabledAction(
    private val target: Task,
    private val dependencyType: Class<out Task>,
    private val dependencyName: String
) : Action<TaskExecutionGraph> {
    override fun execute(graph: TaskExecutionGraph) {
        if (graph.hasTask(target)) {
            graph.getDependencies(target)
                .filter { it.name == dependencyName }
                .filterIsInstance(dependencyType)
                .forEach { target.enabled = target.enabled and it.enabled }
        }
    }
}
