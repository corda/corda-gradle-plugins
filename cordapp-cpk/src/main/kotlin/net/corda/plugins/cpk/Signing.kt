package net.corda.plugins.cpk

import net.corda.plugins.cpk.signing.SigningOptions
import net.corda.plugins.cpk.signing.SigningOptions.Companion.SYSTEM_PROPERTY_PREFIX
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused", "PlatformExtensionReceiverOfInline")
open class Signing @Inject constructor(objects: ObjectFactory, providers: ProviderFactory) {

    @get:Input
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + "enabled").orElse("true").map(String::toBoolean)
    )

    @get:Nested
    val options: SigningOptions = objects.newInstance(SigningOptions::class.java)

    fun options(action: Action<in SigningOptions>) {
        action.execute(options)
    }
}