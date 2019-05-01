package net.corda.plugins

import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.tasks.Input

open class PackageOwnership (@get:Input val name: String) {

    @get:Input
    var keystore: String? = null

    @get:Input
    var keystorePassword: String? = null

    @get:Input
    var keystoreAlias: String? = null

    fun toConfigObject(): ConfigObject {
        return ConfigValueFactory.fromMap(mapOf("packageName" to name,
                "keystore" to keystore,
                "keystorePassword" to keystorePassword,
                "keystoreAlias" to keystoreAlias))
    }
}
