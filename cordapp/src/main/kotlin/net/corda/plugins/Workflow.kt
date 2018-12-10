package net.corda.plugins

import org.gradle.api.tasks.Input

open class Workflow {
    @get:Input
    var name: String? = null
    @get:Input
    var versionId: String? = null
    @get:Input
    var vendor: String? = null
    @get:Input
    var licence: String? = null
}