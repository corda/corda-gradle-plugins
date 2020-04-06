package net.corda.plugins

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
/**
 * Stores the database configuration when deploying databases using Dockerform
 *
 */
open class DbSettings {
    @get:Optional
    @get:Input
    var dbHost: String? = null

    @get:Optional
    @get:Input
    var dbPort: Int? = null

    @get:Optional
    @get:Input
    var dbSchema: String? = null

    @get:Optional
    @get:Input
    var dbName: String? = null

    @get:Optional
    @get:Input
    var dbDockerfile: String? = null

    @get:Optional
    @get:Input
    var dbInit: String? = null

    @get:Optional
    @get:Input
    var dbUrl: String? = null
}