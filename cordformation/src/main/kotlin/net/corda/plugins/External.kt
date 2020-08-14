package net.corda.plugins;

import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Input

open class External {

    @get:Optional
    @get:Input
    var containerName: String = "external-service"

    @get:Optional
    @get:Input
    var servicePorts: List<Int> = listOf(8080)

    @get:Optional
    @get:Input
    var containerImage = ""

    @get:Optional
    @get:Input
    var volumes: List<Map<String, String>> = emptyList()

    @get:Optional
    @get:Input
    var environment: Map<String, Any> = emptyMap()

    @get:Optional
    @get:Input
    var commands: String = ""

    @get:Optional
    @get:Input
    var privileged: Boolean = false

    fun isEmpty(): Boolean {
        return volumes.isEmpty() && containerImage.isEmpty()
    }

}
