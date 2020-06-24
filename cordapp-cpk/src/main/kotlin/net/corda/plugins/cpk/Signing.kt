package net.corda.plugins.cpk

import net.corda.plugins.cpk.signing.SigningOptions
import net.corda.plugins.cpk.signing.SigningOptions.Companion.SYSTEM_PROPERTY_PREFIX
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused")
open class Signing @Inject constructor(objectFactory: ObjectFactory) {

    @get:Input
    val enabled: Property<Boolean> = objectFactory.property(Boolean::class.javaObjectType)
            .convention(System.getProperty(SYSTEM_PROPERTY_PREFIX + "enabled", "true").toBoolean())

    fun enabled(value: Boolean) {
        enabled.set(value)
    }

    @get:Nested
    val options: SigningOptions = objectFactory.newInstance(SigningOptions::class.java)

    fun options(action: Action<in SigningOptions>) {
        action.execute(options)
    }
}