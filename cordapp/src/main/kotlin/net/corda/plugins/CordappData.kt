package net.corda.plugins

import org.gradle.api.tasks.Input

open class CordappData {
    @get:Input
    var name: String? = null
    /** relaxed type so users can specify Integer or String identifiers */
    @get:Input
    var versionId: Int? = null
    @get:Input
    var vendor: String? = null
    @get:Input
    var licence: String? = null
    internal fun isEmpty(): Boolean = (name == null && versionId == null && vendor == null && licence == null)
}