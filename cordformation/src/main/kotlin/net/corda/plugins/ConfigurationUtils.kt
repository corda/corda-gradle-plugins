package net.corda.plugins

import org.gradle.api.InvalidUserDataException
import java.net.URI
import java.net.URISyntaxException

class ConfigurationUtils {

    companion object {

        fun parsePort(address: String): Int {
            return try {
                URI(null, address, null, null, null).port
            } catch (ex: URISyntaxException) {
                throw InvalidUserDataException("Invalid host and port syntax for RPC address, expected host:port. Using default value")
            }
        }
    }
}