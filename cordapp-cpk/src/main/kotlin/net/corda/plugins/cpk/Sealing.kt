package net.corda.plugins.cpk

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused")
open class Sealing @Inject constructor(objects: ObjectFactory) {

    @get:Input
    val enabled: Property<Boolean> = objects.property(Boolean::class.javaObjectType)
            .convention(System.getProperty( "sealing.enabled", "true").toBoolean())

    fun enabled(value: Boolean) {
        enabled.set(value)
    }
}
