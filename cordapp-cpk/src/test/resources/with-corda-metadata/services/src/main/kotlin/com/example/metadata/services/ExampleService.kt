@file:Suppress("unused", "PackageDirectoryMismatch")
package com.example.metadata.services

import net.corda.core.node.services.CordaService
import net.corda.v5.serialization.SerializeAsToken

@CordaService
class ExampleService : SerializeAsToken {
    class Nested : SerializeAsToken
    inner class Inner : SerializeAsToken
}
