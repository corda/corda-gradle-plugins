@file:JvmName("CordformationUtils")
package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
import org.gradle.api.tasks.bundling.Jar
import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * [ClassLoader] for this CordformationUtils class. This is
 * currently the best way I've found to get the [Class] for
 * a Kotlin file from within Kotlin.
 */
private val classLoader = MethodHandles.lookup().lookupClass().classLoader

private const val CORDA_CPK_TASK_NAME = "cpk"

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
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.isTransitive = false
        }
        parent.extendsFrom(configuration)
        configuration
    }
}

fun ConfigurationContainer.createRuntimeOnlyConfiguration(name: String): Configuration {
    return createChildConfiguration(name, getByName(RUNTIME_ONLY_CONFIGURATION_NAME))
}

fun ConfigurationContainer.createCompileConfiguration(name: String): Configuration {
    return createCompileConfiguration(name, "Implementation")
}

private fun ConfigurationContainer.createCompileConfiguration(name: String, testSuffix: String): Configuration {
    return findByName(name) ?: run {
        val configuration = create(name).apply {
            isCanBeConsumed = false
            isCanBeResolved = false
        }
        getByName(COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(configuration)
        matching { it.name.endsWith(testSuffix) }.configureEach { cfg ->
            cfg.extendsFrom(configuration)
        }
        configuration
    }
}

val FileSystemLocationProperty<*>.asPath: Path get() {
    return asFile.get().toPath()
}

internal val Project.cpkTasks get() = tasks.withType(Jar::class.java).matching { task ->
    task.name == CORDA_CPK_TASK_NAME
}

fun writeResourceToFile(resourcePath: String, path: Path) {
    classLoader.getResourceAsStream(resourcePath)?.use {
        Files.copy(it, path, REPLACE_EXISTING)
    }
}

internal fun Config.copyTo(key: String, target: Config, targetKey: String = key): Config {
    return if (hasPath(key)) {
        target + (targetKey to getValue(key))
    } else {
        target
    }
}

internal fun Config.copyKeysTo(target: Config, keys: Iterable<String>) = this + keys.filter { target.hasPath(it) }.associateWith { target.getAnyRef(it) }
internal operator fun Config.plus(property: Pair<String, Any>): Config = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))
internal operator fun Config.plus(properties: Map<String, Any>): Config {
    var out = this
    for ((key, value) in properties) {
        out = out.withValue(key, ConfigValueFactory.fromAnyRef(value))
    }
    return out
}
