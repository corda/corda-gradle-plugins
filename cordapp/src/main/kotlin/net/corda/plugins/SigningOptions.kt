package net.corda.plugins

import org.gradle.api.tasks.Input

/** JAR sign options. */
//Option used for ANT task "signjar"
open class SigningOptions {
    companion object {
        // Defaults to keyStore resource/certificates/cordadevcakeys.jks with Corda developer (non-production) certificates
        const val DEFAULT_ALIAS = "cordaintermediateca"
        const val DEFAULT_STOREPASS = "cordacadevpass"
        const val DEFAULT_KEYSTORE = "cordadevcakeys.jks"
        const val DEFAULT_STORETYPE = "JKS"
        const val DEFAULT_KEYPASS = "cordacadevkeypass"
        // jarSigner doesn't add "SIG-" prefix, and EC signature doesn't follow JAR File Specification
        const val DEFAULT_SIGFILE = "SIG-CORDAINT"
    }

    @get:Input
    var alias = DEFAULT_ALIAS
    fun alias(value: String) { alias = value }

    @get:Input
    var storepass = DEFAULT_STOREPASS
    fun storepass(value: String) { storepass = value }

    @get:Input
    var keystore = DEFAULT_KEYSTORE
    fun keystore(value: String) { keystore = value }

    @get:Input
    var storetype = DEFAULT_STORETYPE
    fun storetype(value: String) { storetype = value }

    @get:Input
    var keypass = DEFAULT_KEYPASS
    fun keypass(value: String) { keypass = value }

    @get:Input
    var sigfile = DEFAULT_SIGFILE
    fun sigfile(value: String) { sigfile = value }

    @get:Input
    var signedjar = ""
    fun signedjar(value: String) { signedjar = value }

    @get:Input
    var verbose = ""
    fun verbose(value: String) { verbose = value }

    @get:Input
    var strict = ""
    fun strict(value: String) { strict = value }

    @get:Input
    var internalsf = ""
    fun internalsf(value: String) { internalsf = value }

    @get:Input
    var sectionsonly = ""
    fun sectionsonly(value: String) { sectionsonly = value }

    @get:Input
    var lazy = ""
    fun lazy(value: String) { lazy = value }

    @get:Input
    var maxmemory = ""
    fun maxmemory(value: String) { maxmemory = value }

    @get:Input
    var preservelastmodified = ""
    fun preservelastmodified(value: String) { preservelastmodified = value }

    @get:Input
    var tsaurl = ""
    fun tsaurl(value: String) { tsaurl = value }

    @get:Input
    var tsacert = ""
    fun tsacert(value: String) { tsacert = value }

    @get:Input
    var tsaproxyhost = ""
    fun tsaproxyhost(value: String) { tsaproxyhost = value }

    @get:Input
    var tsaproxyport = ""
    fun tsaproxyport(value: String) { tsaproxyport = value }

    @get:Input
    var executable = ""
    fun executable(value: String) { executable = value }

    @get:Input
    var force = ""
    fun force(value: String) { force = value }

    @get:Input
    var sigalg = ""
    fun sigalg(value: String) { sigalg = value }

    @get:Input
    var digestalg = ""
    fun digestalg(value: String) { digestalg = value }

    @get:Input
    var tsadigestalg = ""
    fun tsadigestalg(value: String) { tsadigestalg = value }

    fun toSignJarOptionsMap() = mapOf("alias" to alias, "storepass" to storepass,
            "keystore" to keystore, "storetype" to storetype, "keypass" to keypass,
            "sigfile" to sigfile, "signedjar" to signedjar, "verbose" to verbose,
            "strict" to strict, "internalsf" to internalsf, "sectionsonly" to sectionsonly,
            "lazy" to lazy, "maxmemory" to maxmemory, "preservelastmodified" to preservelastmodified,
            "tsaurl" to tsacert, "tsacert" to tsaurl, "tsaproxyhost" to tsaproxyhost,
            "tsaproxyport" to tsaproxyport, "executable" to executable, "force" to force,
            "sigalg" to sigalg, "digestalg" to digestalg, "tsadigestalg" to tsadigestalg)
            .filter { it.value.isNotBlank() }.toMutableMap()

    fun hasDefaultOptions() = keystore == DEFAULT_KEYSTORE && alias == DEFAULT_ALIAS
            && storepass == DEFAULT_STOREPASS &&  keypass == DEFAULT_KEYPASS
            //DEFAULT_STORETYPE and DEFAULT_SIGFILE not checked as they are more a derived properties
}