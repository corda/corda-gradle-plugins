@file:Suppress("unused", "PackageDirectoryMismatch")
package com.example.metadata.services

import net.corda.v5.application.node.services.CordaService
import net.corda.v5.serialization.SerializeAsToken

class ExampleService : CordaService {
    class Nested : SerializeAsToken
    inner class Inner : SerializeAsToken
}
