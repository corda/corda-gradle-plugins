package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused")
open class CordappData @Inject constructor(objects: ObjectFactory) {
    @get:Input
    val name: Property<String> = objects.property(String::class.java)

    /** relaxed type so users can specify Integer or String identifiers */
    @get:Input
    val versionId: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val vendor: Property<String> = objects.property(String::class.java)

    @get:Input
    val licence: Property<String> = objects.property(String::class.java)

    @Internal
    internal fun isEmpty(): Boolean = (!name.isPresent && !versionId.isPresent && !vendor.isPresent && !licence.isPresent)

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