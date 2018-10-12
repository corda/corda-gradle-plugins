package net.corda.plugins

import groovy.lang.Closure
import org.gradle.api.tasks.Input
import org.gradle.util.ConfigureUtil

/** JAR sign and control option. */
open class Signing {

    @get:Input
    var enabled: Boolean = true
        private set

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun enabled(value: String?) {
        enabled = value?.let { it.toBoolean() } ?: true
    }

    @get:Input
    var options: SigningOptions = SigningOptions()
        private set

    fun options(configureClosure: Closure<in SigningOptions>) {
        options = ConfigureUtil.configure(configureClosure, options) as SigningOptions
    }
}