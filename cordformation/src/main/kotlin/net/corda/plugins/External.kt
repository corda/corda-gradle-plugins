package net.corda.plugins;

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

open class External @Inject constructor(objects: ObjectFactory) {

    @get:Input
    val containerName: Property<String> = objects.property(String::class.java).convention("external-service")

    @get:Input
    val servicePorts: ListProperty<Int> = objects.listProperty(Int::class.java).convention(listOf(8080))

    @get:Input
    val containerImage: Property<String> = objects.property(String::class.java).convention("")

    @Suppress("unchecked-cast")
    @get:Input
    val volumes: ListProperty<Map<String, String>> = objects.listProperty(Map::class.java) as ListProperty<Map<String, String>>

    @get:Input
    val environment: MapProperty<String, Any> = objects.mapProperty(String::class.java, Any::class.java)

    @get:Input
    val commands: Property<String> = objects.property(String::class.java).convention("")

    @get:Input
    val privileged: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

}
