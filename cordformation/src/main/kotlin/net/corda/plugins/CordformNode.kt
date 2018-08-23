package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.util.Collections.emptyList

open class CordformNode {
    private companion object {
        val DEFAULT_HOST = "localhost"
    }

    /**
     * Name of the node. Node will be placed in directory based on this name - all lowercase with whitespaces removed.
     * Actual node name inside node.conf will be as set here.
     */
    @get:Input
    var name: String? = null
        private set

    /**
     * p2p Port.
     */
    var p2pPort = 10002
        private set

    /**
     * RPC Port.
     */
    var rpcPort = 10003
        private set

    /**
     * Set the RPC users for this node. This configuration block allows arbitrary configuration.
     * The recommended current structure is:
     * [[['username': "username_here", 'password': "password_here", 'permissions': ["permissions_here"]]]
     * The above is a list to a map of keys to values using Groovy map and list shorthands.
     *
     * Incorrect configurations will not cause a DSL error.
     */
    @Input
    var rpcUsers = emptyList<Map<String, Any>>()

    /**
     * Apply the notary configuration if this node is a notary. The map is the config structure of
     * net.corda.node.services.config.NotaryConfig
     */
    @Optional
    @Input
    var notary: Map<String, Any>? = null

    var extraConfig: Map<String, Any>? = null

    /**
     * Copy files into the node relative directory './drivers'.
     */
    @Optional
    @Input
    var drivers: List<String>? = null

    var config = ConfigFactory.empty()
        protected set

    /**
     * Get the artemis address for this node.
     *
     * @return This node's P2P address.
     */
    val p2pAddress: String
        @Input
        get() = config.getString("p2pAddress")

    /**
     * Returns the RPC address for this node, or null if one hasn't been specified.
     */
    val rpcAddress: String?
        @Optional
        @Input
        get() = if (config.hasPath("rpcSettings.address")) {
            config.getConfig("rpcSettings").getString("address")
        } else getOptionalString("rpcAddress")

    /**
     * Returns the address of the web server that will connect to the node, or null if one hasn't been specified.
     */
    val webAddress: String?
        @Optional
        @Input
        get() = getOptionalString("webAddress")

    @get:Optional
    @get:Input
    var configFile: String? = null
        private set

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    fun name(name: String) {
        this.name = name
        setValue("myLegalName", name)
    }

    /**
     * Set the Artemis P2P port for this node on localhost.
     *
     * @param p2pPort The Artemis messaging queue port.
     */
    fun p2pPort(p2pPort: Int) {
        p2pAddress(DEFAULT_HOST + ':'.toString() + p2pPort)
        this.p2pPort = p2pPort
    }

    /**
     * Set the Artemis P2P address for this node.
     *
     * @param p2pAddress The Artemis messaging queue host and port.
     */
    fun p2pAddress(p2pAddress: String) {
        setValue("p2pAddress", p2pAddress)
    }

    /**
     * Enable/disable the development mode
     *
     * @param devMode - true if devMode is enabled
     */
    fun devMode(devMode: Boolean?) {
        setValue("devMode", devMode)
    }

    /**
     * Set the Artemis RPC port for this node on localhost.
     *
     * @param rpcPort The Artemis RPC queue port.
     */
    @Deprecated("Use {@link CordformNode#rpcSettings(RpcSettings)} instead.")
    fun rpcPort(rpcPort: Int) {
        rpcAddress(DEFAULT_HOST + ':'.toString() + rpcPort)
        this.rpcPort = rpcPort
    }

    /**
     * Set the Artemis RPC address for this node.
     *
     * @param rpcAddress The Artemis RPC queue host and port.
     */
    @Deprecated("Use {@link CordformNode#rpcSettings(RpcSettings)} instead.")
    fun rpcAddress(rpcAddress: String) {
        setValue("rpcAddress", rpcAddress)
    }

    /**
     * Configure a webserver to connect to the node via RPC. This port will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    fun webPort(webPort: Int) {
        webAddress(DEFAULT_HOST + ':'.toString() + webPort)
    }

    /**
     * Configure a webserver to connect to the node via RPC. This address will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    fun webAddress(webAddress: String) {
        setValue("webAddress", webAddress)
    }

    /**
     * Specifies RPC settings for the node.
     */
    fun rpcSettings(settings: RpcSettings) {
        config = settings.addTo("rpcSettings", config)
    }

    /**
     * Set the path to a file with optional properties, which are appended to the generated node.conf file.
     *
     * @param configFile The file path.
     */
    fun configFile(configFile: String) {
        this.configFile = configFile
    }

    private fun getOptionalString(path: String): String? {
        return if (config.hasPath(path)) config.getString(path) else null
    }

    private fun setValue(path: String, value: Any?) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }
}