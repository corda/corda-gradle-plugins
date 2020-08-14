package net.corda.plugins;

import org.gradle.api.tasks.Input

open class External {

    @get:Input
    var containerName: String = "external-service"

    @get:Input
    var servicePorts: List<Int> = listOf(8080)

    @get:Input
    var containerImage = ""

    @get:Input
    var volumes: List<Map<String, String>> = emptyList()

    @get:Input
    var environment: Map<String, Any> = emptyMap()

    @get:Input
    var commands: String = ""

    @get:Input
    var privileged: Boolean = false

    fun isEmpty(): Boolean {
        return volumes.isEmpty() && containerImage.isEmpty()
    }

}
