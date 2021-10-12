package net.corda.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskInputs
import javax.inject.Inject

/**
 * Registers these [CordappExtension] properties as task inputs,
 * because Gradle cannot "see" their `@Input` annotations yet.
 */
fun TaskInputs.nested(nestName: String, cordapp: CordappExtension) {
    property("${nestName}.targetPlatformVersion", cordapp.targetPlatformVersion)
    property("${nestName}.minimumPlatformVersion", cordapp.minimumPlatformVersion)
    nested("${nestName}.contract", cordapp.contract)
    nested("${nestName}.workflow", cordapp.workflow)
    nested("${nestName}.signing", cordapp.signing)
    nested("${nestName}.sealing", cordapp.sealing)
}

@Suppress("UnstableApiUsage", "Unused")
open class CordappExtension @Inject constructor(objects: ObjectFactory)  {

    /**
     * Top-level CorDapp attributes
     */
    @get:Input
    val targetPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val minimumPlatformVersion: Property<Int> = objects.property(Int::class.java).convention(targetPlatformVersion)

    /**
     * CorDapp Contract distribution information.
     */
    @get:Nested
    val contract: CordappData = objects.newInstance(CordappData::class.java)

    /**
     * CorDapp Worflow (flows and services) distribution information.
     */
    @get:Nested
    val workflow: CordappData = objects.newInstance(CordappData::class.java)

    /**
     * Optional parameters for ANT signJar tasks to sign Cordapps.
     */
    @get:Nested
    val signing: Signing = objects.newInstance(Signing::class.java)

    /**
     * Optional marker to seal all packages in the JAR.
     */
    @get:Nested
    val sealing: Sealing = objects.newInstance(Sealing::class.java)

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
