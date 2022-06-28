@file:Suppress("PackageDirectoryMismatch")
package net.corda.v5.application.flows

open class FlowException(message: String?, cause: Throwable?) : Exception(message, cause)
