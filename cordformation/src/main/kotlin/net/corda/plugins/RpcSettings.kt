package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import java.net.URI
import java.net.URISyntaxException

class RpcSettings {
    private var config = ConfigFactory.empty()

    @get:Input
    var port = 10003
        private set
    @get:Input
    var adminPort = 10005
        private set

    /**
     * RPC address for the node.
     */
    fun address(value: String) {
        val portNumber = parsePort(value)
        port = when (portNumber) { -1 -> port else -> portNumber }
        setValue("address", value)
    }

    /**
     * RPC Port for the node
     */
    fun port(value: Int) {
        this.port = value
        setValue("address", "localhost:$port")
    }

    /**
     * RPC admin address for the node (necessary if [useSsl] is false or unset).
     */
    fun adminAddress(value: String) {
        val portNumber = parsePort(value)
        adminPort = when (portNumber) { -1 -> adminPort else -> portNumber }
        setValue("adminAddress", value)
    }

    fun adminPort(value: Int) {
        this.adminPort = value
        setValue("adminAddress", "localhost:$adminPort")
    }

    /**
     * Specifies whether the node RPC layer will require SSL from clients.
     */
    fun useSsl(value: Boolean?) {
        setValue("useSsl", value)
    }

    /**
     * Specifies whether the RPC broker is separate from the node.
     */
    fun standAloneBroker(value: Boolean?) {
        setValue("standAloneBroker", value)
    }

    /**
     * Specifies SSL certificates options for the RPC layer.
     */
    fun ssl(options: SslOptions) {
        config = options.addTo("ssl", config)
    }

    fun addTo(key: String, config: Config): Config {
        return if (this.config.isEmpty) {
            config
        } else config.withValue(key, this.config.root())
    }

    private fun setValue(path: String, value: Any?) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }

    private fun parsePort(address: String): Int {
        return try {
            URI(null, address, null, null, null).port
        } catch (ex: URISyntaxException) {
            throw InvalidUserDataException("Invalid host and port syntax for RPC address, expected host:port. Using default value")
        }
    }
}