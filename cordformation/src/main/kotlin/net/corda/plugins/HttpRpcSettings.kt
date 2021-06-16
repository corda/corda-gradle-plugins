package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.tasks.Input

open class HttpRpcSettings {
    private var config = ConfigFactory.empty().withValue("context", ConfigValueFactory.fromMap(emptyMap()))

    @get:Input
    var port = 10003
        private set

    /**
     * HTTP-RPC address for the node.
     */
    fun address(value: String) {
        val parsedValue = ConfigurationUtils.parsePort(value)
        port = when (parsedValue) {
            -1 -> port
            else -> parsedValue
        }
        setValue("address", value)
    }

    /**
     * HTTP-RPC Port for the node
     */
    fun port(value: Int) {
        this.port = value
        setValue("address", "localhost:$port")
    }

    private fun setValue(path: String, value: Any?) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }

    fun addTo(key: String, config: Config): Config {
        return if (this.config.isEmpty) {
            config
        } else config.withValue(key, this.config.root())
    }
}