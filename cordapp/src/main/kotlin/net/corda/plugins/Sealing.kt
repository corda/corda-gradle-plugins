package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskInputs
import javax.inject.Inject

/**
 * Registers these [Sealing] properties as task inputs,
 * because Gradle cannot "see" their `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, sealing: Sealing) {
    property("${nestName}.enabled", sealing.enabled)
}

@Suppress("UnstableApiUsage", "Unused")
open class Sealing @Inject constructor(objects: ObjectFactory, providers: ProviderFactory) {

    @get:Input
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(
        providers.systemProperty("sealing.enabled")
            .orElse("true")
            .map(String::toBoolean)
    )

    fun enabled(value: Boolean) {
        enabled.set(value)
    }

    fun enabled(value: String) {
        enabled.set(value.toBoolean())
    }
}
