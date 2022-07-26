@file:JvmName("CordappProperties")
package net.corda.plugins.cpk2

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
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
    property("${nestName}.sealing", cordapp.sealing)
    property("${nestName}.bndVersion", cordapp.bndVersion)
}

@Suppress("UnstableApiUsage", "Unused", "PlatformExtensionReceiverOfInline")
open class CordappExtension @Inject constructor(
    objects: ObjectFactory,
    providers: ProviderFactory,
    osgiVersion: String,
    bndVersion: String
) {
    /**
     * Top-level CorDapp attributes
     */
    @get:Input
    val targetPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val minimumPlatformVersion: Property<Int> = objects.property(Int::class.java)
            .convention(PLATFORM_VERSION_X)

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

    @get:Input
    val osgiVersion: Property<String> = objects.property(String::class.java).convention(osgiVersion)

    /**
     * This property only provides the default value for [CPKDependenciesTask.hashAlgorithm], which is
     * annotated with @Input. Hence this is not a task input itself.
     */
    val hashAlgorithm: Property<String> = objects.property(String::class.java).convention("SHA-256")

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
