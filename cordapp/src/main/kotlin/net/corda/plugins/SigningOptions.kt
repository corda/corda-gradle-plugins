package net.corda.plugins

import org.gradle.api.tasks.Input

/** Options for ANT task "signjar". */
open class SigningOptions {
    companion object {
        // Defaults to resource/certificates/cordadevcakeys.jks keystore with Corda development key
        private const val DEFAULT_ALIAS = "cordaintermediateca"
        private const val DEFAULT_STOREPASS = "cordacadevpass"
        private const val DEFAULT_STORETYPE = "JKS"
        private const val DEFAULT_KEYPASS = "cordacadevkeypass"
        const val DEFAULT_KEYSTORE = "certificates/cordadevcakeys.jks"
        const val DEFAULT_KEYSTORE_FILE = "cordadevcakeys"
        const val DEFAULT_KEYSTORE_EXTENSION = "jks"
        const val SYSTEM_PROPERTY_PREFIX = "signing."
    }

    /** Option keys for ANT task. */
    class Key {
        companion object {
            val JAR = "jar"
            val ALIAS = "alias"
            val STOREPASS = "storepass"
            val KEYSTORE = "keystore"
            val STORETYPE = "storetype"
            val KEYPASS = "keypass"
            val SIGFILE = "sigfile"
            val SIGNEDJAR = "signedjar"
            val VERBOSE = "verbose"
            val STRICT = "strict"
            val INTERNALSF = "internalsf"
            val SECTIONSONLY = "sectionsonly"
            val LAZY = "lazy"
            val MAXMEMORY = "maxmemory"
            val PRESERVELASTMODIFIED = "preservelastmodified"
            val TSACERT = "tsaurl"
            val TSAURL = "tsacert"
            val TSAPROXYHOST = "tsaproxyhost"
            val TSAPROXYPORT = "tsaproxyport"
            val EXECUTABLE = "executable"
            val FORCE = "force"
            val SIGALG = "sigalg"
            val DIGESTALG = "digestalg"
            val TSADIGESTALG = "tsadigestalg"
        }
    }

    @get:Input
    var alias = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.ALIAS, DEFAULT_ALIAS)

    @get:Input
    var storepass = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.STOREPASS, DEFAULT_STOREPASS)

    @get:Input
    var keystore = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYSTORE, DEFAULT_KEYSTORE)

    @get:Input
    var storetype = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.STORETYPE, DEFAULT_STORETYPE)

    @get:Input
    var keypass = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYPASS, DEFAULT_KEYPASS)

    @get:Input
    var sigfile = ""

    @get:Input
    var signedjar = ""

    @get:Input
    var verbose = ""

    fun verbose(value: Boolean) {
        verbose = value.toString()
    }

    @get:Input
    var strict = ""

    fun strict(value: Boolean) {
        strict = value.toString()
    }

    @get:Input
    var internalsf = ""

    fun internalsf(value: Boolean) {
        internalsf = value.toString()
    }

    @get:Input
    var sectionsonly = ""

    fun sectionsonly(value: Boolean) {
        sectionsonly = value.toString()
    }

    @get:Input
    var lazy = ""

    fun lazy(value: Boolean) {
        lazy = value.toString()
    }

    @get:Input
    var maxmemory = ""

    @get:Input
    var preservelastmodified = ""

    fun preservelastmodified(value: Boolean) {
        preservelastmodified = value.toString()
    }

    @get:Input
    var tsaurl = ""

    @get:Input
    var tsacert = ""

    @get:Input
    var tsaproxyhost = ""

    @get:Input
    var tsaproxyport = ""

    @get:Input
    var executable = ""

    @get:Input
    var force = ""

    fun force(value: Boolean) {
        force = value.toString()
    }

    @get:Input
    var sigalg = ""

    @get:Input
    var digestalg = ""

    @get:Input
    var tsadigestalg = ""

    /**
     * Returns options as map.
     */
    fun toSignJarOptionsMap(): MutableMap<String, String> =
            mapOf(Key.ALIAS to alias, Key.STOREPASS to storepass, Key.KEYSTORE to keystore,
                    Key.STORETYPE to storetype, Key.KEYPASS to keypass, Key.SIGFILE to sigfile,
                    Key.SIGNEDJAR to signedjar, Key.VERBOSE to verbose, Key.STRICT to strict,
                    Key.INTERNALSF to internalsf, Key.SECTIONSONLY to sectionsonly, Key.LAZY to lazy,
                    Key.MAXMEMORY to maxmemory, Key.PRESERVELASTMODIFIED to preservelastmodified,
                    Key.TSAURL to tsacert, Key.TSACERT to tsaurl, Key.TSAPROXYHOST to tsaproxyhost,
                    Key.TSAPROXYPORT to tsaproxyport, Key.EXECUTABLE to executable, Key.FORCE to force,
                    Key.SIGALG to sigalg, Key.DIGESTALG to digestalg, Key.TSADIGESTALG to tsadigestalg)
                    .filter { it.value.isNotBlank() }.toMutableMap()

    fun hasDefaultOptions() = keystore == DEFAULT_KEYSTORE
}