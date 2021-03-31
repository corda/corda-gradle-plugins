package net.corda.plugins.cpk

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused", "PlatformExtensionReceiverOfInline")
open class CordappExtension @Inject constructor(
    objects: ObjectFactory,
    providers: ProviderFactory,
    bndVersion: String
) {
    /**
     * Top-level CorDapp attributes
     */
    @get:Input
    val targetPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val minimumPlatformVersion: Property<Int> = objects.property(Int::class.java)

    /**
     * CorDapp Contract distribution information.
     */
    @get:Nested
    val contract: CordappData = objects.newInstance(CordappData::class.java)

    /**
     * CorDapp Workflow (flows and services) distribution information.
     */
    @get:Nested
    val workflow: CordappData = objects.newInstance(CordappData::class.java)

    /**
     * Optional parameters for ANT signJar tasks to sign CorDapps.
     */
    @get:Nested
    val signing: Signing = objects.newInstance(Signing::class.java)

    /**
     * Optional marker to seal all packages in the JAR.
     */
    @get:Input
    val sealing: Property<Boolean> = objects.property(Boolean::class.java).convention(
        providers.systemProperty(CORDAPP_SEALING_SYSTEM_PROPERTY_NAME).orElse("true").map(String::toBoolean)
    )

    @get:Input
    val bndVersion: Property<String> = objects.property(String::class.java).convention(bndVersion)

    fun contract(action: Action<in CordappData>) {
        action.execute(contract)
    }

    fun workflow(action: Action<in CordappData>) {
        action.execute(workflow)
    }

    fun signing(action: Action<in Signing>) {
        action.execute(signing)
    }

    fun targetPlatformVersion(value: Int?) {
        targetPlatformVersion.set(value)
    }

    fun minimumPlatformVersion(value: Int?) {
        minimumPlatformVersion.set(value)
    }
}
