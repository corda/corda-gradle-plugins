@file:Suppress("PackageDirectoryMismatch", "Unused")
package net.corda.v5.application.services

import net.corda.v5.application.flows.flowservices.dependencies.CordaInjectable
import net.corda.v5.serialization.SerializeAsToken

interface CordaService : SerializeAsToken, CordaInjectable {
    fun onEvent(event: ServiceLifecycleEvent) {}
}
