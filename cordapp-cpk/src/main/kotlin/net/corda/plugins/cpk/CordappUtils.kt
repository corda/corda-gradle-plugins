@file:JvmName("CordappUtils")
package net.corda.plugins.cpk

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import java.util.Collections.unmodifiableSet

const val GROUP_NAME = "Cordapp"

const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDAPP_PACKAGING_CONFIGURATION_NAME = "cordappPackaging"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"
const val CORDA_EMBEDDED_CONFIGURATION_NAME = "cordaEmbedded"
const val CORDA_CPK_CONFIGURATION_NAME = "cordaCPK"

val HARDCODED_EXCLUDES: Set<Pair<String, String>> = unmodifiableSet(setOf(
    "org.jetbrains.kotlin" to "*",
    "net.corda.kotlin" to "*",
    "org.osgi" to "*",
    "org.slf4j" to "slf4j-api",
    "org.slf4j" to "jcl-over-slf4j",
    "commons-logging" to "commons-logging",
    "co.paralleluniverse" to "quasar-core",
    "co.paralleluniverse" to "quasar-core-osgi"
))

private fun ConfigurationContainer.createChildConfiguration(name: String, parent: Configuration): Configuration {
    return findByName(name) ?: run {
        val configuration = create(name) {
            it.isTransitive = false
        }
        parent.extendsFrom(configuration)
        configuration
    }
}

fun ConfigurationContainer.createCompileOnlyConfiguration(name: String): Configuration {
    return findByName(name) ?: run {
        val configuration = create(name)
        getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        matching { it.name.endsWith("CompileOnly") }.configureEach { cfg ->
            cfg.extendsFrom(configuration)
        }
        configuration
    }
}

fun ConfigurationContainer.createRuntimeOnlyConfiguration(name: String): Configuration {
    return createChildConfiguration(name, getByName(RUNTIME_ONLY_CONFIGURATION_NAME))
}

val List<String>.isJavaIdentifiers: Boolean get() {
    return this.all(String::isJavaIdentifier)
}

val String.isJavaIdentifier: Boolean get() {
    if (isEmpty() || !Character.isJavaIdentifierStart(this[0])) {
        return false
    }

    var idx = length
    while (--idx > 0) {
        if (!Character.isJavaIdentifierPart(this[idx])) {
            return false
        }
    }

    return true
}
