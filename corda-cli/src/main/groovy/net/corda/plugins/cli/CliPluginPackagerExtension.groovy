package net.corda.plugins.cli

import org.gradle.api.provider.Property

interface CliPluginPackagerExtension {
    Property<String> getCliPluginId()
    Property<String> getCliPluginClass()
    Property<String> getCliPluginProvider()
    Property<String> getCliPluginDescription()
}
