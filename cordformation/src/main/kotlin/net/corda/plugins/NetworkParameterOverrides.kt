package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValueFactory
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class NetworkParameterOverrides @Inject constructor(project: Project) {

    @get:Nested
    val packageOwnership: NamedDomainObjectContainer<PackageOwnership> = project.container(PackageOwnership::class.java)

    fun packageOwnership(action: Action<NamedDomainObjectContainer<in PackageOwnership>>) {
        action.execute(packageOwnership)
    }

    fun toConfig(): Config {
        val packageOwnershipsList = mutableListOf<ConfigObject?>()
        packageOwnership.forEach { packageOwnershipsList.add(it.toConfigObject()) }
        val packageOwnershipsConfigObjectList = ConfigValueFactory.fromIterable(packageOwnershipsList)

        return ConfigFactory.empty().withValue("networkParameterOverrides", ConfigValueFactory.fromMap(mapOf("packageOwnership" to packageOwnershipsConfigObjectList)))
    }
}