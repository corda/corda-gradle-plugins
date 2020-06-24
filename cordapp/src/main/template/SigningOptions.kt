package @root_package@.signing

import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * !!! GENERATED FILE - DO NOT EDIT !!!
 * See cordapp/src/main/template/SigningOptions.kt instead.
 */

/** Options for ANT task "signjar". */
open class SigningOptions {
    companion object {
        // Defaults to resource/certificates/cordadevcakeys.jks keystore with Corda development key
        private const val DEFAULT_ALIAS = "cordacodesign"
        private const val DEFAULT_STOREPASS = "cordacadevpass"
        private const val DEFAULT_STORETYPE = "JKS"
        private const val DEFAULT_KEYPASS = "cordacadevkeypass"
        const val DEFAULT_KEYSTORE = "certificates/cordadevcodesign.jks"
        const val DEFAULT_KEYSTORE_FILE = "cordadevcakeys"
        const val DEFAULT_KEYSTORE_EXTENSION = "jks"
        const val SYSTEM_PROPERTY_PREFIX = "signing."
    }

    /** Option keys for ANT task. */
    class Key {
        companion object {
            const val JAR = "jar"
            const val ALIAS = "alias"
            const val STOREPASS = "storepass"
            const val KEYSTORE = "keystore"
            const val STORETYPE = "storetype"
            const val KEYPASS = "keypass"
            const val SIGFILE = "sigfile"
            const val SIGNEDJAR = "signedjar"
            const val VERBOSE = "verbose"
            const val STRICT = "strict"
            const val INTERNALSF = "internalsf"
            const val SECTIONSONLY = "sectionsonly"
            const val LAZY = "lazy"
            const val MAXMEMORY = "maxmemory"
            const val PRESERVELASTMODIFIED = "preservelastmodified"
            const val TSACERT = "tsaurl"
            const val TSAURL = "tsacert"
            const val TSAPROXYHOST = "tsaproxyhost"
            const val TSAPROXYPORT = "tsaproxyport"
            const val EXECUTABLE = "executable"
            const val FORCE = "force"
            const val SIGALG = "sigalg"
            const val DIGESTALG = "digestalg"
            const val TSADIGESTALG = "tsadigestalg"
        }
    }

    @get:Input
    var alias: String = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.ALIAS, DEFAULT_ALIAS)

    @get:Input
    var storepass: String = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.STOREPASS, DEFAULT_STOREPASS)

    @get:Input
    var keystore: String = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYSTORE, DEFAULT_KEYSTORE)

    @get:Input
    var storetype: String = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.STORETYPE, DEFAULT_STORETYPE)

    @get:Input
    var keypass: String = System.getProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYPASS, DEFAULT_KEYPASS)

    @get:Input
    var sigfile: String = "cordapp"

    @get:Input
    var signedjar: String = ""

    @get:Console
    var verbose: String = ""

    fun verbose(value: Boolean) {
        verbose = value.toString()
    }

    @get:Input
    var strict: String = ""

    fun strict(value: Boolean) {
        strict = value.toString()
    }

    @get:Input
    var internalsf: String = ""

    fun internalsf(value: Boolean) {
        internalsf = value.toString()
    }

    @get:Input
    var sectionsonly: String = ""

    fun sectionsonly(value: Boolean) {
        sectionsonly = value.toString()
    }

    @get:Input
    var lazy: String = ""

    fun lazy(value: Boolean) {
        lazy = value.toString()
    }

    @get:Internal
    var maxmemory: String = ""

    @get:Input
    var preservelastmodified: String = ""

    fun preservelastmodified(value: Boolean) {
        preservelastmodified = value.toString()
    }

    @get:Input
    var tsaurl: String = ""

    @get:Input
    var tsacert: String = ""

    @get:Input
    var tsaproxyhost: String = ""

    @get:Input
    var tsaproxyport: String = ""

    @get:Input
    var executable: String = ""

    @get:Input
    var force: String = ""

    fun force(value: Boolean) {
        force = value.toString()
    }

    @get:Input
    var sigalg: String = ""

    @get:Input
    var digestalg: String = ""

    @get:Input
    var tsadigestalg: String = ""

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
                    .filterTo(LinkedHashMap()) { it.value.isNotBlank() }

    fun hasDefaultOptions() = keystore == DEFAULT_KEYSTORE
}