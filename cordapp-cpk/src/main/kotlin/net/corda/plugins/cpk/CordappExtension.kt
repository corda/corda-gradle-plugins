package net.corda.plugins.cpk

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused")
open class CordappExtension @Inject constructor(objectFactory: ObjectFactory)  {

    /**
     * Top-level CorDapp attributes
     */
    @get:Input
    val targetPlatformVersion: Property<Int> = objectFactory.property(Int::class.java)

    @get:Input
    val minimumPlatformVersion: Property<Int> = objectFactory.property(Int::class.java)

    /**
     * CorDapp Contract distribution information.
     */
    @get:Nested
    val contract: CordappData = objectFactory.newInstance(CordappData::class.java)

    /**
     * CorDapp Worflow (flows and services) distribution information.
     */
    @get:Nested
    val workflow: CordappData = objectFactory.newInstance(CordappData::class.java)

    /**
     * Optional parameters for ANT signJar tasks to sign Cordapps.
     */
    @get:Nested
    val signing: Signing = objectFactory.newInstance(Signing::class.java)

    /**
     * Optional marker to seal all packages in the JAR.
     */
    @get:Nested
    val sealing: Sealing = objectFactory.newInstance(Sealing::class.java)

    fun contract(action: Action<in CordappData>) {
        action.execute(contract)
    }

    fun workflow(action: Action<in CordappData>) {
        action.execute(workflow)
    }

    fun signing(action: Action<in Signing>) {
        action.execute(signing)
    }

    fun sealing(action: Action<in Sealing>) {
        action.execute(sealing)
    }

    fun targetPlatformVersion(value: Int?) {
        targetPlatformVersion.set(value)
    }

    fun minimumPlatformVersion(value: Int?) {
        minimumPlatformVersion.set(value)
    }
}