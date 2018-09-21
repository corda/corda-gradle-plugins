package net.corda.plugins

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import javax.inject.Inject

/** JAR sign, keystore generation and overall signing control options. */
class Signing @Inject constructor(private val project: Project){

    @get:Input
    var all: Boolean = true
        private set
    fun all(value: Boolean) { all = value }

    @get:Input
    var enabled: Boolean = true
        private set
    fun enabled(value: Boolean) { enabled = value }

    @get:Input
    var generateKeystore: Boolean = true
        private set
    fun generateKeystore(value: Boolean) { generateKeystore = value }

    @get:Input
    var options: SigningOptions = SigningOptions()
        private set
    fun options(configureClosure: Closure<in SigningOptions>) {
        options = project.configure(SigningOptions(), configureClosure) as SigningOptions
    }
}