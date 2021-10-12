package net.corda.plugins

import net.corda.plugins.cordformation.signing.SigningOptions
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

/** Options for ANT tasks "genkey" and "signjar". */
open class KeyGenAndSigningOptions @Inject constructor(
    objects: ObjectFactory,
    providers: ProviderFactory
) : SigningOptions(objects, providers) {
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
    val keyAlgorithm: Property<String> = objects.property(String::class.java).convention("RSA")

    @get:Optional
    @get:Input
    val dname: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val validity: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:Input
    val keySize: Property<Int> = objects.property(Int::class.java)

    private val _genKeyOptions = objects.mapProperty(String::class.java, String::class.java).apply {
        put(SigningOptions.Key.ALIAS, alias)
        put(SigningOptions.Key.STOREPASS, storePassword)
        put(SigningOptions.Key.STORETYPE, storeType)
        put(SigningOptions.Key.KEYPASS, keyPassword)
        put(SigningOptions.Key.SIGALG, signatureAlgorithm)
        put(SigningOptions.Key.VERBOSE, verbose.map(Boolean::toString))
        put(Key.KEYALG, keyAlgorithm)
    }

    @get:Internal
    val genKeyOptions: Provider<out MutableMap<String, String>> = _genKeyOptions.map { opts ->
        val result = LinkedHashMap(opts)
        result.setOptional(SigningOptions.Key.KEYSTORE, keyStore)
        result.setOptional(Key.DNAME, dname)
        result.setOptional(Key.VALIDITY, validity)
        result.setOptional(Key.KEYSIZE, keySize)
        result
    }
}
