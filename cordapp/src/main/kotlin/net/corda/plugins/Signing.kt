package net.corda.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

/** JAR sign and control option. */
open class Signing @Inject constructor(objectFactory: ObjectFactory) {

    @get:Input
    var enabled: Boolean = System.getProperty("signingEnabled", "true").toBoolean()

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