@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasAll

class SecondaryConstructorWithDefaultToStub(private val message: String, private val data: Long) : HasAll {
    @StubMeOut constructor(message: String = "<default-value>") : this(message, 0)

    override fun stringData(): String = message
    override fun longData(): Long = data
    override fun intData(): Int = -1
}