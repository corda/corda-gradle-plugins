@file:Suppress("PackageDirectoryMismatch")
package com.example.workflow

import com.google.common.collect.ImmutableSet
import net.corda.v5.application.flows.Flow

class ExampleWorkflow : Flow<Set<String>> {
    override fun call(): Set<String> {
        return ImmutableSet.of("Hello World!")
    }
}
