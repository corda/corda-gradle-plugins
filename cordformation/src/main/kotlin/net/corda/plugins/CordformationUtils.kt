@file:JvmName("CordformationUtils")
package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileSystemLocationProperty
import java.nio.file.Path

const val CORDAPP_PLUGIN_ID = "net.corda.plugins.cordapp"
const val CORDA_BOOTSTRAPPER_CONFIGURATION_NAME = "cordaBootstrapper"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_CORDAPP_CONFIGURATION_NAME = "cordaCordapp"
const val CORDA_DRIVER_CONFIGURATION_NAME = "cordaDriver"
const val DEPLOY_BOOTSTRAPPER_CONFIGURATION_NAME = "deployBootstrapper"
const val DEPLOY_CORDAPP_CONFIGURATION_NAME = "deployCordapp"
const val DEPLOY_CORDFORMATION_CONFIGURATION_NAME = "deployCordformation"
const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDA_CONFIGURATION_NAME = "corda"

/**
 * Mimics the "project.ext" functionality in groovy which provides a direct
 * accessor to the "ext" extension (See: ExtraPropertiesExtension)
 */
fun Project.findRootProperty(name: String): String? {
    return rootProject.findProperty(name)?.toString()
}

fun ConfigurationContainer.createBasicConfiguration(name: String): Configuration {
    return maybeCreate(name)
        .setTransitive(false)
        .setVisible(false)
        .also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
        }
}

val FileSystemLocationProperty<*>.asPath: Path get() {
    return asFile.get().toPath()
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
