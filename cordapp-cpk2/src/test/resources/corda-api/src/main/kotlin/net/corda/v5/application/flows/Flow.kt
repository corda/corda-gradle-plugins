@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.application.flows

fun interface Flow<out T> {
    @Throws(FlowException::class)
    fun call(): T
}
