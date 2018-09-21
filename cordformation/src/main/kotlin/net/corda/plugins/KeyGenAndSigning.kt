package net.corda.plugins

import groovy.lang.Closure
import org.gradle.api.tasks.Input
import org.gradle.util.ConfigureUtil

/** JAR sign, keystore generation and overall signing control options. */
open class KeyGenAndSigning {
    @get:Input
    var enabled: Boolean = true
        private set
    fun enabled(value: Boolean) { enabled = value }

    @get:Input
    var options: KeyGenAndSigningOptions = KeyGenAndSigningOptions()
        private set
    fun options(configureClosure: Closure<in KeyGenAndSigningOptions>) {
        options = ConfigureUtil.configure(configureClosure, options) as KeyGenAndSigningOptions
    }

    @get:Input
    var all: Boolean = true
        private set
    fun all(value: Boolean) { all = value }

    @get:Input
    var generateKeystore: Boolean = true
        private set
    fun generateKeystore(value: Boolean) { generateKeystore = value }
}