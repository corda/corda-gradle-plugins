package net.corda.plugins

import net.corda.plugins.cordformation.signing.SigningOptions
import org.gradle.api.tasks.Input

/** Options for ANT tasks "genkey" and "signjar". */
open class KeyGenAndSigningOptions : SigningOptions() {
    /** Additional option keys for ANT task. */
    class Key {
        companion object {
            const val KEYALG = "keyalg"
            const val DNAME = "dname"
            const val VALIDITY = "validity"
            const val KEYSIZE = "keysize"
        }
    }

    @get:Input
    var keyalg = "RSA"

    @get:Input
    var dname = ""

    @get:Input
    var validity = ""

    @get:Input
    var keysize = ""

    fun toGenKeyOptionsMap(): MutableMap<String, String> =
            mapOf(SigningOptions.Key.ALIAS to alias, SigningOptions.Key.STOREPASS to storepass,
                    SigningOptions.Key.KEYSTORE to keystore, SigningOptions.Key.STORETYPE to storetype,
                    SigningOptions.Key.KEYPASS to keypass, SigningOptions.Key.SIGALG to sigalg, SigningOptions.Key.VERBOSE to verbose,
                    Key.KEYALG to keyalg, Key.DNAME to dname, Key.VALIDITY to validity, Key.KEYSIZE to keysize)
                    .filter { it.value.isNotBlank() }.toMutableMap()
}
