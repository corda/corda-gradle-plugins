package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.File
import javax.inject.Inject

open class Cordapp @Inject constructor(
        @get:[Optional Input] val coordinates: String?,
        @get:[Optional Input] val project: Project?
) {
    constructor(coordinates: String) : this(coordinates, null)
    constructor(cordappProject: Project) : this(null, cordappProject)

    // The configuration text that will be written
    @get:Optional
    @get:Input
    internal var config: String? = null

    /**
     * Determines whether or not CordFormation will deploy this CorDapp into Corda.
     */
    @get:Input
    var deploy: Boolean = true

    /**
     * Set the configuration text that will be written to the cordapp's configuration file
     */
    fun config(config: String) {
        this.config = config
    }

    /**
     * Reads config from the file and later writes it to the cordapp's configuration file
     */
    fun config(configFile: File) {
        this.config = configFile.readText()
    }
}