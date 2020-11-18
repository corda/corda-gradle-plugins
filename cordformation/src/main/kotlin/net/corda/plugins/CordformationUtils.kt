@file:JvmName("CordformationUtils")
package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private val classLoader = object {}::class.java.classLoader

/**
 * Mimics the "project.ext" functionality in groovy which provides a direct
 * accessor to the "ext" extension (See: ExtraPropertiesExtension)
 */
fun Project.findRootProperty(name: String): String? {
    return rootProject.findProperty(name)?.toString()
}

fun Project.configuration(name: String): Configuration = configurations.getByName(name)

fun ConfigurationContainer.createChildConfiguration(name: String, parent: Configuration): Configuration {
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

fun createTempFileFromResource(resourcePath: String, tempFileName: String, tempFileExtension: String): Path {
    val path = Files.createTempFile(tempFileName, tempFileExtension)
    classLoader.getResourceAsStream(resourcePath)?.use {
        Files.copy(it, path, REPLACE_EXISTING)
    }
    path.toFile().deleteOnExit()
    return path
}

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
