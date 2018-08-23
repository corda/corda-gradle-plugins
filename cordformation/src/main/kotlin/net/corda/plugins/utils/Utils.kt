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

internal fun Config.copyKeysTo(target: Config, keys: Iterable<String>): Config {
    for(key in keys) {
        this.copyTo(key, target)
    }
    return target
}

internal operator fun Config.plus(property: Pair<String, Any>): Config = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))