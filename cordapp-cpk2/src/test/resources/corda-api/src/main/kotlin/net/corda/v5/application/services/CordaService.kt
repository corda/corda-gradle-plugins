@file:Suppress("PackageDirectoryMismatch", "Unused")
package net.corda.v5.application.services

import net.corda.v5.application.services.lifecycle.ServiceLifecycleEvent
import net.corda.v5.serialization.SingletonSerializeAsToken

interface CordaService : SingletonSerializeAsToken {
    fun onEvent(event: ServiceLifecycleEvent) {}
}
