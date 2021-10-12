package net.corda.plugins

import com.typesafe.config.*
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.time.Duration
import javax.inject.Inject

@Suppress("UnstableApiUsage", "Unused")
open class NetworkParameterOverrides @Inject constructor(objects: ObjectFactory) {

    @get:Optional
    @get:Input
    val minimumPlatformVersion: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:Input
    val maxMessageSize: Property<Int> = objects.property(Int::class.java)

    @get:Optional
    @get:Input
    val maxTransactionSize: Property<Int> = objects.property(Int::class.java)

    @get:Nested
    val packageOwnership: NamedDomainObjectContainer<PackageOwnership> = objects.domainObjectContainer(PackageOwnership::class.java)

    fun packageOwnership(action: Action<in NamedDomainObjectContainer<PackageOwnership>>) {
        action.execute(packageOwnership)
    }

    @get:Optional
    @get:Input
    val eventHorizon: Property<Duration> = objects.property(Duration::class.java)

    @Internal
    fun isEmpty() = !minimumPlatformVersion.isPresent &&
            !maxMessageSize.isPresent &&
            !maxTransactionSize.isPresent &&
            packageOwnership.isEmpty() &&
            !eventHorizon.isPresent

    fun toConfig(): Config {
        val map = sequenceOf(
            "eventHorizon" to eventHorizon.orNull,
            "packageOwnership" to packageOwnership
                    .takeIf (NamedDomainObjectContainer<PackageOwnership>::isNotEmpty)
                    ?.let { po -> ConfigValueFactory.fromIterable(po.map { it.toConfigObject() }) },
            "minimumPlatformVersion" to minimumPlatformVersion.orNull,
            "maxMessageSize" to maxMessageSize.orNull,
            "maxTransactionSize" to maxTransactionSize.orNull
        ).filter { it.second != null }.toMap()

        return ConfigFactory.empty().withValue("networkParameterOverrides",
                ConfigValueFactory.fromMap(map)
        )
    }
}