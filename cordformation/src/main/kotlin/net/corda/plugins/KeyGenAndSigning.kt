package net.corda.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class KeyGenAndSigning @Inject constructor(objectFactory: ObjectFactory) {
    @get:Input
    var enabled: Boolean = true

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun enabled(value: String) {
        enabled = value.toBoolean()
    }

    @get:Input
    var all: Boolean = true

    fun all(value: Boolean) {
        all = value
    }

    fun all(value: String) {
        all = value.toBoolean()
    }

    @get:Input
    var generateKeystore: Boolean = false

    fun generateKeystore(value: Boolean) {
        generateKeystore = value
    }

    fun generateKeystore(value: String) {
        generateKeystore = value.toBoolean()
    }

    @get:Nested
    var options: KeyGenAndSigningOptions = objectFactory.newInstance(KeyGenAndSigningOptions::class.java)

    fun options(action: Action<in KeyGenAndSigningOptions>) {
        action.execute(options)
    }
}