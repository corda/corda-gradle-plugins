package net.corda.plugins

import net.corda.plugins.cordapp.signing.SigningOptions
import net.corda.plugins.cordapp.signing.SigningOptions.Companion.SYSTEM_PROPERTY_PREFIX
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class Signing @Inject constructor(objectFactory: ObjectFactory) {

    @get:Input
    var enabled: Boolean = System.getProperty(SYSTEM_PROPERTY_PREFIX + "enabled", "true").toBoolean()

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun enabled(value: String) {
        enabled = value.toBoolean()
    }

    @get:Nested
    val options: SigningOptions = objectFactory.newInstance(SigningOptions::class.java)

    fun options(action: Action<in SigningOptions>) {
        action.execute(options)
    }
}