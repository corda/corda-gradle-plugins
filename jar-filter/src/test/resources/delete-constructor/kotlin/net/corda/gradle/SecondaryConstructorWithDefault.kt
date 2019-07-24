@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString

class SecondaryConstructorWithDefault(private val message: String, private val data: Long) : HasString, HasLong {
    @DeleteMe constructor(message: String = "<default-value>") : this(message, 0)

    override fun stringData(): String = message
    override fun longData(): Long = data
}