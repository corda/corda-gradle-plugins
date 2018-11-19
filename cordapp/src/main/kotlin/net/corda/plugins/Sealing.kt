package net.corda.plugins

import org.gradle.api.tasks.Input

open class Sealing {

    @get:Input
    var enabled: Boolean = System.getProperty( "sealing.enabled", "true").toBoolean()

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun enabled(value: String) {
        enabled = value.toBoolean()
    }
}
