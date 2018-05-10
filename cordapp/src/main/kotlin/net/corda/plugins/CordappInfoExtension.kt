package net.corda.plugins

import org.gradle.api.Project

open class CordappInfoExtension {
    private lateinit var project: Project

    fun setProject(project: Project) {
        this.project = project
    }

    /**
     * CorDapp's name. Default is project's group id dot JAR's base name.
     */
    var name: String? = null

    /**
     * CorDapp's version. Default is project's version.
     */
    var version: String? = null

    /**
     * CorDapp's vendor. Default is "Unknown", which will issue warnings and might prevent a Corda node from starting the CorDapp in the future.
     */
    var vendor: String? = null
}