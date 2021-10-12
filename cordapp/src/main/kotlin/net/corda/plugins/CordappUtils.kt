@file:JvmName("CordappUtils")
package net.corda.plugins

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

const val CORDAPP_TASK_GROUP = "Cordapp"

fun ConfigurationContainer.createBasicConfiguration(name: String): Configuration {
    return maybeCreate(name)
        .setTransitive(false)
        .setVisible(false)
        .also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
        }
}
