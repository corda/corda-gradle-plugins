package net.corda.plugins

import org.gradle.api.tasks.Input
import java.nio.file.Path

//contains union of properties for ANT tasks "genkey" and "signjar" as most of them are shared by both tasks
class Signing {

    var defaultKeyStoreFileName = "jarSignKeystore.jks"
    var defaultStoretype = "jks"
    var defaultStorepass = "secret1!"
    var defaultAlias = "cordapp-signer"
    var defaultDname = "OU=Dummy Cordapp Distributor, O=Corda, L=London, C=GB"

    @get:Input
    var all: Boolean = false
        private set
    @get:Input
    var enabled: Boolean = true
        private set
    @get:Input
    var generateKeystore: Boolean = true
        private set

    private var opts = mutableMapOf("storetype" to defaultStoretype,
            "alias" to defaultAlias,
            "storepass" to defaultStorepass,
            "dname" to defaultDname,
            "keyalg" to "RSA") //required by Corda
        set(value) {
            opts.putAll(value)
        }

    fun all(value: Boolean) {
        all = value
    }

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun generateKeystore(value: Boolean) {
        generateKeystore = value
    }

    fun genKeyTaskOptions(baseDirectory: Path): Map<String, String> {
        opts.putIfAbsent("keystore", baseDirectory.resolve(defaultKeyStoreFileName).toAbsolutePath().normalize().toString())
        val allowedGenKeyTaskOptions = setOf("alias", "storepass", "keystore", "storetype", "keypass", "sigalg", "keyalg",
                "verbose", "dname", "validity", "keysize")
        return opts.filterKeys { allowedGenKeyTaskOptions.contains(it) }
    }

    fun hasDefaultKeystoreOptions(baseDirectory: Path) =
        opts["alias"] == defaultAlias && opts["storepass"] == defaultStorepass
                && opts["keystore"] == baseDirectory.resolve(defaultKeyStoreFileName).toAbsolutePath().normalize().toString()

    fun singJarTaskOptions(baseDirectory: Path, jarToSign: Path): Map<String, String> {
        opts.putIfAbsent("keystore", baseDirectory.resolve(defaultKeyStoreFileName).toAbsolutePath().normalize().toString())
        opts["jar"] = jarToSign.toString()
        val allowedSignJarTaskOptions = setOf("jar", "alias", "storepass", "keystore", "storetype", "keypass", "sigfile",
                "signedjar", "verbose", "strict", "internalsf", "sectionsonly", "lazy", "maxmemory", "preservelastmodified",
                "tsaurl", "tsacert", "tsaproxyhost", "tsaproxyport", "executable", "force", "sigalg", "digestalg", "tsadigestalg")
        return opts.filterKeys { allowedSignJarTaskOptions.contains(it) }
    }
}