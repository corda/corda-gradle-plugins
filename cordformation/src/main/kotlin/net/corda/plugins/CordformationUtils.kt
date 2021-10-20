@file:JvmName("CordformationUtils")
@file:Suppress("deprecation")

package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPlugin.COMPILE_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CONFIGURATION_NAME

const val CORDA_RUNTIME_CONFIGURATION_NAME = "cordaRuntime"
const val CORDA_DRIVER_CONFIGURATION_NAME = "cordaDriver"
const val CORDAPP_CONFIGURATION_NAME = "cordapp"

/**
 * Mimics the "project.ext" functionality in groovy which provides a direct
 * accessor to the "ext" extension (See: ExtraPropertiesExtension)
 */
fun Project.findRootProperty(name: String): String? {
    return rootProject.findProperty(name)?.toString()
}

fun createChildConfiguration(name: String, parent: Configuration, configurations: ConfigurationContainer): Configuration {
    return configurations.findByName(name) ?: run {
        val configuration = configurations.create(name) {
            it.isTransitive = false
        }
        parent.extendsFrom(configuration)
        configuration
    }
}

fun createCompileConfiguration(name: String, configurations: ConfigurationContainer): Configuration {
    return createChildConfiguration(name, configurations.getByName(COMPILE_CONFIGURATION_NAME), configurations)
}

fun createRuntimeConfiguration(name: String, configurations: ConfigurationContainer): Configuration {
    return createChildConfiguration(name, configurations.getByName(RUNTIME_CONFIGURATION_NAME), configurations)
}

internal fun Config.copyTo(key: String, target: Config, targetKey: String = key): Config {
    return if (hasPath(key)) {
        target + (targetKey to getValue(key))
    } else {
        target
    }
}

internal fun Config.copyKeysTo(target: Config, keys: Iterable<String>) = this + keys.filter(target::hasPath).associateWith(target::getAnyRef)

internal operator fun Config.plus(property: Pair<String, Any>): Config = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))
internal operator fun Config.plus(properties: Map<String, Any>): Config {
    var out = this
    for ((key, value) in properties) {
        out = out.withValue(key, ConfigValueFactory.fromAnyRef(value))
    }
    return out
}
