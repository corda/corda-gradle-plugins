@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("StaticFieldsToDelete")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
@JvmField
val stringField: String = "<default-value>"

@DeleteMe
@JvmField
val longField: Long = 123456789L

@DeleteMe
@JvmField
val intField: Int = 123456
