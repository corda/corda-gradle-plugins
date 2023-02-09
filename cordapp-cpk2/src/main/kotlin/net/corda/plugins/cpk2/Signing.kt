@file:JvmName("SigningProperties")
package net.corda.plugins.cpk2

import net.corda.plugins.cpk2.signing.SigningOptions
import net.corda.plugins.cpk2.signing.SigningOptions.Companion.SYSTEM_PROPERTY_PREFIX
import net.corda.plugins.cpk2.signing.nested
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskInputs
import javax.inject.Inject

/**
 * Registers these [Signing] properties as task inputs,
 * because Gradle cannot "see" their `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, signing: Signing) {
    property("${nestName}.enabled", signing.enabled)
    nested("${nestName}.options", signing.options)
}

@Suppress("Unused")
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
