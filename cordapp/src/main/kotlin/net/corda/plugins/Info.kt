package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import javax.inject.Inject

@Deprecated("Use top-level attributes and specific Contract and Workflow info objects")
@Suppress("UnstableApiUsage", "Unused")
open class Info @Inject constructor(objects: ObjectFactory) {
    @get:Optional
    @get:Input
    val name: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val version: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val vendor: Property<String> = objects.property(String::class.java)

    @get:Optional
    @get:Input
    val targetPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:Input
    val minimumPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @Internal
    internal fun isEmpty() : Boolean = (!name.isPresent && !version.isPresent && !vendor.isPresent
            && !targetPlatformVersion.isPresent
            && !minimumPlatformVersion.isPresent)

    fun name(value: String?) {
        name.set(value)
    }

    fun version(value: String?) {
        version.set(value)
    }

    fun vendor(value: String?) {
        vendor.set(value)
    }

    fun targetPlatformVersion(value: Int?) {
        targetPlatformVersion.set(value)
    }

    fun minimumPlatformVersion(value: Int?) {
        minimumPlatformVersion.set(value)
    }
}