package net.corda.plugins

import org.gradle.api.Project

open class CordappExtension {
    private lateinit var project: Project

    fun setProject(project: Project) {
        this.project = project
        info = project.extensions.create("info", Info::class.java)
        signing = project.extensions.create("signing", Signing::class.java)
    }

    /**
     * CorDapp distribution information.
     */
    var info: Info? = null

    /**
     * Optional parameters for ANT signJar tasks to sign Cordapps
     */
    var signing: Signing = Signing()
}