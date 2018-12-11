package net.corda.plugins

import org.gradle.api.tasks.Input

@Deprecated("Use top-level attributes and specific Contract and Workflow info objects")
open class Info {
    @get:Input
    var name: String? = null
    @get:Input
    var version: String? = null
    @get:Input
    var vendor: String? = null
    @get:Input
    var targetPlatformVersion: Int? = null
    @get:Input
    var minimumPlatformVersion: Int? = null
}