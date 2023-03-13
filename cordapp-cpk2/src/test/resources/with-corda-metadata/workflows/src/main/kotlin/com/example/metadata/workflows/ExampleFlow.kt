@file:Suppress("unused", "PackageDirectoryMismatch")
package com.example.metadata.workflows

import com.example.metadata.contracts.ExampleState
import net.corda.v5.application.flows.Flow

class ExampleFlow(private val state: ExampleState) : Flow<String> {
    private companion object {
        private const val MESSAGE = "Hello, OSGi!"
        private const val DATA = "OSGi Data"
    }

    override fun call(): String {
        @Suppress("ObjectLiteralToLambda")
        val innerFlow = object : Flow<String> {
            override fun call(): String {
                return MESSAGE
            }
        }
        val lambdaFlow = Flow { DATA }
        return "$state -> ${innerFlow.call()} AND ${lambdaFlow.call()}"
    }

    class NestedFlow : Flow<String> {
        override fun call(): String = throw UnsupportedOperationException(this::class.java.name)
    }

    inner class InnerFlow : Flow<String> {
        override fun call(): String = throw UnsupportedOperationException(this::class.java.name)
    }
}
