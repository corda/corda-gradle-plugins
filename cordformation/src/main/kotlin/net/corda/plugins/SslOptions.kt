package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

class SslOptions {
    private var config = ConfigFactory.empty()

    /**
     * Password for the keystore.
     */
    fun keyStorePassword(value: String) {
        setValue("keyStorePassword", value)
    }

    /**
     * Password for the truststore.
     */
    fun trustStorePassword(value: String) {
        setValue("trustStorePassword", value)
    }

    /**
     * Directory under which key stores are to be placed.
     */
    fun certificatesDirectory(value: String) {
        setValue("certificatesDirectory", value)
    }

    /**
     * Absolute path to SSL keystore. Default: "[certificatesDirectory]/sslkeystore.jks"
     */
    fun sslKeystore(value: String) {
        setValue("sslKeystore", value)
    }

    /**
     * Absolute path to SSL truststore. Default: "[certificatesDirectory]/truststore.jks"
     */
    fun trustStoreFile(value: String) {
        setValue("trustStoreFile", value)
    }

    fun addTo(key: String, config: Config): Config {
        return if (this.config.isEmpty) {
            config
        } else config.withValue(key, this.config.root())
    }

    private fun setValue(path: String, value: Any) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }
}