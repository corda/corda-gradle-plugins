package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

class DataSourceProperties {
    private var config = ConfigFactory.empty()

    /**
     * Full class name of the JDBC driver
     */
    fun dataSourceClassName(className : String) = setValue("dataSourceClassName", className)

    /**
     * Database user
     */
    fun url(value: String) = setValue("dataSource.url", value)

    /**
     * Database user
     */
    fun user(value: String) = setValue("dataSource.user", value)

    /**
     * Database password.
     */
    fun password(value: String) = setValue("dataSource.password", value)

    fun addTo(key: String, config: Config): Config {
        return if (this.config.isEmpty) {
            config
        } else config.withValue(key, this.config.root())
    }

    private fun setValue(path: String, value: Any) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    }
}