package net.corda.core.flows

open class FlowException(message: String?, cause: Throwable?) : Exception(message, cause)
