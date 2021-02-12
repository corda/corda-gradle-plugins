@file:JvmName("CordappUtils")
package net.corda.plugins.cpk

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import kotlin.math.max

const val GROUP_NAME = "Cordapp"

const val CORDAPP_CONFIGURATION_NAME = "cordapp"
const val CORDA_RUNTIME_ONLY_CONFIGURATION_NAME = "cordaRuntimeOnly"
const val CORDA_PROVIDED_CONFIGURATION_NAME = "cordaProvided"

fun compareVersions(v1: String, v2: String): Int {
    val parsed1 = parseVersionString(v1)
    val parsed2 = parseVersionString(v2)
    for (i in 0 until max(parsed1.count(), parsed2.count())) {
        val compareResult = parsed1.getOrElse(i) { 0 }.compareTo(parsed2.getOrElse(i) { 0 })
        if (compareResult != 0) {
            return compareResult
        }
    }
    return 0
}

private fun parseVersionString(v: String) = v.split('.').flatMap { it.split('-') }.map {
    try {
        it.toInt()
    } catch (e: NumberFormatException) {
        -1
    }
}

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
        configuration
    }
}

fun ConfigurationContainer.createRuntimeOnlyConfiguration(name: String): Configuration {
    return createChildConfiguration(name, getByName(RUNTIME_ONLY_CONFIGURATION_NAME))
}
