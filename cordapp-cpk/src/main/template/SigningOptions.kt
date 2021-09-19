@file:JvmName("SigningOptionsProperties")
package @root_package@.signing

import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskInputs
import java.io.File
import java.net.URI
import javax.inject.Inject

/**
 * !!! GENERATED FILE - DO NOT EDIT !!!
 * See cordapp-cpk/src/main/template/SigningOptions.kt instead.
 */

/**
 * Registers the [SigningOptions] properties as task inputs,
 * because Gradle cannot "see" the `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, options: SigningOptions) {
    property("${nestName}.alias", options.alias)
    property("${nestName}.keyStore", options.keyStore).optional(true)
    property("${nestName}.signatureFileName", options.signatureFileName)
    property("${nestName}.strict", options.strict)
    property("${nestName}.internalSF", options.internalSF)
    property("${nestName}.sectionsOnly", options.sectionsOnly)
    property("${nestName}.lazy", options.lazy)
    property("${nestName}.preserveLastModified", options.preserveLastModified)
    property("${nestName}.tsaCert", options.tsaCert).optional(true)
    property("${nestName}.tsaUrl", options.tsaUrl).optional(true)
    property("${nestName}.tsaProxyHost", options.tsaProxyHost).optional(true)
    property("${nestName}.tsaProxyPort", options.tsaProxyPort).optional(true)
    file(options.executable).withPropertyName("${nestName}.executable")
        .withPathSensitivity(RELATIVE)
        .optional()
    property("${nestName}.force", options.force)
    property("${nestName}.signatureAlgorithm", options.signatureAlgorithm).optional(true)
    property("${nestName}.digestAlgorithm", options.digestAlgorithm).optional(true)
    property("${nestName}.tsaDigestAlgorithm", options.tsaDigestAlgorithm).optional(true)
}

/** Options for ANT task "signjar". */
@Suppress("UnstableApiUsage")
open class SigningOptions @Inject constructor(objects: ObjectFactory, providers: ProviderFactory) {
    companion object {
        // Defaults to META-INF/certificates/cordadevcodesign.p12 keystore with Corda development key
        private const val DEFAULT_ALIAS = "cordacodesign"
        private const val DEFAULT_STOREPASS = "cordacadevpass"
        private const val DEFAULT_STORETYPE = "PKCS12"
        private const val DEFAULT_SIGFILE = "cordapp"
        const val DEFAULT_KEYSTORE = "META-INF/certificates/cordadevcodesign.p12"
        const val DEFAULT_KEYSTORE_FILE = "cordadevcakeys"
        const val DEFAULT_KEYSTORE_EXTENSION = ".p12"
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
            const val TSACERT = "tsacert"
            const val TSAURL = "tsaurl"
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
    val alias: Property<String> = objects.property(String::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.ALIAS).orElse(DEFAULT_ALIAS)
    )

    @get:Internal
    val storePassword: Property<String> = objects.property(String::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.STOREPASS).orElse(DEFAULT_STOREPASS)
    )

    @get:Optional
    @get:Input
    val keyStore: Property<URI> = objects.property(URI::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYSTORE)
            .forUseAtConfigurationTime()
            .map(URI::create)
    )

    fun setKeyStore(value: RegularFile?) {
        setKeyStore(value?.asFile)
    }

    fun setKeyStore(value: File?) {
        keyStore.set(value?.absoluteFile?.toURI())
    }

    fun setKeyStore(value: String?) {
        keyStore.set(value?.let(URI::create))
    }

    @get:Internal
    val storeType: Property<String> = objects.property(String::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.STORETYPE).orElse(DEFAULT_STORETYPE)
    )

    @get:Internal
    val keyPassword: Property<String> = objects.property(String::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYPASS)
            .forUseAtConfigurationTime()
            .orElse(storePassword)
    )

    @get:Input
    val signatureFileName: Property<String> = objects.property(String::class.java).convention(
        providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.SIGFILE)
            .forUseAtConfigurationTime()
            .orElse(alias.map { aliasValue ->
                if (aliasValue == DEFAULT_ALIAS) {
                    DEFAULT_SIGFILE
                } else {
                    aliasValue
                }
            })
    )

    @get:Console
    val verbose: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Input
    val strict: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Input
    val internalSF: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Input
    val sectionsOnly: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Input
    val lazy: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Internal
    val maxMemory: Property<String> = objects.property(String::class.java)

    @get:Input
    val preserveLastModified: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Optional
    @get:Input
    val tsaUrl: Property<URI> = objects.property(URI::class.java)

    fun setTsaUrl(value: String?) {
        tsaUrl.set(value?.let(URI::create))
    }

    @get:Optional
    @get:Input
    val tsaCert: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val tsaProxyHost: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val tsaProxyPort: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:InputFile
    @get:PathSensitive(RELATIVE)
    val executable: RegularFileProperty = objects.fileProperty()

    @get:Input
    val force: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Optional
    @get:Input
    val signatureAlgorithm: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val digestAlgorithm: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val tsaDigestAlgorithm: Property<String> = objects.property(String::class.java)

    private val _signJarOptions = objects.mapProperty(String::class.java, String::class.java).apply {
        put(Key.ALIAS, alias)
        put(Key.STOREPASS, storePassword)
        put(Key.STORETYPE, storeType)
        put(Key.KEYPASS, keyPassword)
        put(Key.SIGFILE, signatureFileName)
        put(Key.VERBOSE, verbose.map(Boolean::toString))
        put(Key.STRICT, strict.map(Boolean::toString))
        put(Key.INTERNALSF, internalSF.map(Boolean::toString))
        put(Key.SECTIONSONLY, sectionsOnly.map(Boolean::toString))
        put(Key.LAZY, lazy.map(Boolean::toString))
        put(Key.PRESERVELASTMODIFIED, preserveLastModified.map(Boolean::toString))
        put(Key.FORCE, force.map(Boolean::toString))
    }

    open fun values(options: SigningOptions): SigningOptions {
        // DO NOT COPY THE FOLLOWING PROPERTIES!
        // - signatureFileName
        alias.set(options.alias)
        storePassword.set(options.storePassword)
        keyStore.set(options.keyStore)
        storeType.set(options.storeType)
        keyPassword.set(options.keyPassword)
        verbose.set(options.verbose)
        strict.set(options.strict)
        internalSF.set(options.internalSF)
        sectionsOnly.set(options.sectionsOnly)
        lazy.set(options.lazy)
        maxMemory.set(options.maxMemory)
        preserveLastModified.set(options.preserveLastModified)
        tsaUrl.set(options.tsaUrl)
        tsaCert.set(options.tsaCert)
        tsaDigestAlgorithm.set(options.tsaDigestAlgorithm)
        tsaProxyHost.set(options.tsaProxyHost)
        tsaProxyPort.set(options.tsaProxyPort)
        executable.set(options.executable)
        force.set(options.force)
        signatureAlgorithm.set(options.signatureAlgorithm)
        digestAlgorithm.set(options.digestAlgorithm)
        return this
    }

    protected fun MutableMap<String, String>.setOptional(key: String, value: Property<*>) {
        if (value.isPresent) {
            this[key] = value.get().toString()
        }
    }

    protected fun MutableMap<String, String>.setOptional(key: String, value: RegularFileProperty) {
        if (value.isPresent) {
            this[key] = value.asFile.get().absolutePath
        }
    }

    /**
     * Returns options as map.
     */
    @get:Internal
    val signJarOptions: Provider<out MutableMap<String, String>> = _signJarOptions.map { opts ->
        val result = LinkedHashMap(opts)
        result.setOptional(Key.KEYSTORE, keyStore)
        result.setOptional(Key.MAXMEMORY, maxMemory)
        result.setOptional(Key.TSAURL, tsaUrl)
        result.setOptional(Key.TSACERT, tsaCert)
        result.setOptional(Key.TSAPROXYHOST, tsaProxyHost)
        result.setOptional(Key.TSAPROXYPORT, tsaProxyPort)
        result.setOptional(Key.EXECUTABLE, executable)
        result.setOptional(Key.SIGALG, signatureAlgorithm)
        result.setOptional(Key.DIGESTALG, digestAlgorithm)
        result.setOptional(Key.TSADIGESTALG, tsaDigestAlgorithm)
        result
    }
}
