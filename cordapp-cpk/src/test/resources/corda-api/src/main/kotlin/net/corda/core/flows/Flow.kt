@file:Suppress("PackageDirectoryMismatch")
package net.corda.core.flows

fun interface Flow<out T> {
    @Throws(FlowException::class)
    fun call(): T
}
