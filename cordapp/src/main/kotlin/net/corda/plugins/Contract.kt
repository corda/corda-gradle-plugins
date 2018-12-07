package net.corda.plugins

import org.gradle.api.tasks.Input

open class Contract {
    @get:Input
    var name: String? = null
    @get:Input
    var version: String? = null
    @get:Input
    var vendor: String? = null
    @get:Input
    var licence: String? = null
}