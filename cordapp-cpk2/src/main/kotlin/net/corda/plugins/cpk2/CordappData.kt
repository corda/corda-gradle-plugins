@file:JvmName("CordappDataProperties")
package net.corda.plugins.cpk2

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskInputs
import javax.inject.Inject

/**
 * Registers these [CordappData] properties as task inputs,
 * because Gradle cannot "see" their `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, data: CordappData) {
    property("${nestName}.name", data.name).optional(true)
    property("${nestName}.versionId", data.versionId).optional(true)
    property("${nestName}.vendor", data.vendor).optional(true)
    property("${nestName}.licence", data.licence).optional(true)
}

@Suppress("Unused")
open class CordappData @Inject constructor(objects: ObjectFactory) {
    @get:Optional
    @get:Input
    val name: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val versionId: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:Input
    val vendor: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val licence: Property<String> = objects.property(String::class.java)

    internal val isEmpty: Boolean
        get() = (!name.isPresent && !versionId.isPresent && !vendor.isPresent && !licence.isPresent)

    fun name(value: String?) {
        name.set(value)
    }

    fun versionId(value: Int?) {
        versionId.set(value)
    }

    fun vendor(value: String?) {
        vendor.set(value)
    }

    fun licence(value: String?) {
        licence.set(value)
    }
}
