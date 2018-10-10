package net.corda.plugins

import org.gradle.api.tasks.Input

/** JAR sign and keystore generation options, with default values set for minimal required set. */
//Option used for ANT tasks "genkey" and "signjar"
class KeyGenAndSigningOptions : SigningOptions() {

    @get:Input
    var keyalg = "RSA"
    fun keyalg(value: String) { keyalg = value }

    @get:Input
    var dname = ""
    fun dname(value: String) { dname = value }

    @get:Input
    var validity = ""
    fun validity(value: String) { validity = value }

    @get:Input
    var keysize = ""
    fun keysize(value: String) { keysize = value }

    fun toGenKeyOptionsMap() = mapOf("alias" to alias, "storepass" to storepass, "keystore" to keystore,
            "storetype" to storetype, "keypass" to keypass, "sigalg" to sigalg, "keyalg" to keyalg,
            "verbose" to verbose, "dname" to dname, "validity" to validity, "keysize" to keysize)
            .filter { it.value.isNotBlank() }.toMutableMap()
}