package net.corda.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class KeyGenAndSigning @Inject constructor(objects: ObjectFactory) {
    // default behaviour is to deploy JAR artifacts as built (signed or unsigned)
    @get:Input
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Input
    val all: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    @get:Input
    val generateKeystore: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    @get:Nested
    val options: KeyGenAndSigningOptions = objects.newInstance(KeyGenAndSigningOptions::class.java)

    fun options(action: Action<in KeyGenAndSigningOptions>) {
        action.execute(options)
    }
}
