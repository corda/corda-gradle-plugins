@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("HasOverloadedConstructorsToDelete")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.*

private const val DEFAULT_MESSAGE = "<default-value>"

class HasOverloadedStringConstructorToDelete @JvmOverloads @DeleteMe constructor(private val message: String = DEFAULT_MESSAGE) : HasString {
    override fun stringData(): String = message
}

class HasOverloadedLongConstructorToDelete @JvmOverloads @DeleteMe constructor(private val data: Long = 0) : HasLong {
    override fun longData(): Long = data
}

class HasOverloadedIntConstructorToDelete @JvmOverloads @DeleteMe constructor(private val data: Int = 0) : HasInt {
    override fun intData(): Int = data
}

/**
 * This case is complex because:
 *  - The primary constructor has two parameters.
 *  - The first constructor parameter has a default value.
 *  - The second constructor parameter is mandatory.
 */
class HasOverloadedComplexConstructorToDelete @JvmOverloads @DeleteMe constructor(private val data: Int = 0, private val message: String)
    : HasInt, HasString
{
    override fun stringData(): String = message
    override fun intData(): Int = data
}

class HasMultipleStringConstructorsToDelete(private val message: String) : HasString {
    @DeleteMe constructor() : this(DEFAULT_MESSAGE)
    override fun stringData(): String = message
}

class HasMultipleLongConstructorsToDelete(private val data: Long) : HasLong {
    @DeleteMe constructor() : this(0)
    override fun longData(): Long = data
}

class HasMultipleIntConstructorsToDelete(private val data: Int) : HasInt {
    @DeleteMe constructor() : this(0)
    override fun intData(): Int = data
}
