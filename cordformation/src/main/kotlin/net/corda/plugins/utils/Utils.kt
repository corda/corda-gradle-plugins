package net.corda.plugins.utils

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory

internal fun Config.copyTo(key: String, target: Config, targetKey: String = key): Config {
    return if (hasPath(key)) {
        target + (targetKey to getValue(key))
    } else {
        target
    }
}

internal fun Config.copyKeysTo(target: Config, keys: Iterable<String>) = this + keys.filter { target.hasPath(it) }.map { it to target.getAnyRef(it) }.toMap()
internal operator fun Config.plus(property: Pair<String, Any>): Config = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))
internal operator fun Config.plus(properties: Map<String, Any>): Config {
    var out = this
    for ((key, value) in properties) {
        out = out.withValue(key, ConfigValueFactory.fromAnyRef(value))
    }
    return out
}