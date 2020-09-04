package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import javax.inject.Inject

open class External @Inject constructor(objects: ObjectFactory) {

    @get:Input
    val containerName: Property<String> = objects.property(String::class.java).convention("external-service")

    @get:Input
    val servicePorts: ListProperty<Int> = objects.listProperty(Int::class.java).convention(listOf(8080))

    @get:Input
    @get:Optional
    val containerImage: Property<String> = objects.property(String::class.java)
    //This field being tagged optional may seem counter-intuitive, as it is the most important part without which nothing can be done
    // but the tag allows us to check for it complete absence

    @Suppress("unchecked_cast")
    @get:Input
    val volumes: ListProperty<Map<String, String>> = objects.listProperty(Map::class.java) as ListProperty<Map<String, String>>

    @get:Input
    val environment: MapProperty<String, Any> = objects.mapProperty(String::class.java, Any::class.java)

    @get:Input
    @get:Optional
    val commands: Property<String> = objects.property(String::class.java)

    @get:Input
    val privileged: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

}
