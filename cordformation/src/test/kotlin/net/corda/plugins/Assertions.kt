@file:JvmName("Assertions")
package net.corda.plugins

import com.typesafe.config.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

fun assertThatConfig(config: Config) = ConfigAssertions(config)

class ConfigAssertions(private val config: Config) {
    fun hasPath(path: String, expectedValue: String): ConfigAssertions {
        assertTrue(config.hasPath(path), "Config does not contain '$path'")
        assertEquals(expectedValue, config.getString(path))
        return this
    }
}
