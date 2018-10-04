package net.corda.plugins

import groovy.lang.Closure
import org.gradle.api.tasks.Input
import org.gradle.util.ConfigureUtil

/** JAR sign and control option. */
open class Signing {

    @get:Input
    // Depending on invoking task null value indicates the flag not being set and allows to treat it either as true or false by default.
    var enabled: Boolean? = null
        private set
    fun enabled(value: Boolean) { enabled = value }

    @get:Input
    var options: SigningOptions = SigningOptions()
        private set
    fun options(configureClosure: Closure<in SigningOptions>) {
        options = ConfigureUtil.configure(configureClosure, options) as SigningOptions
    }
}