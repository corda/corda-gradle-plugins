@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("StaticValToDelete")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
val stringVal: String = "<default-value>"

@DeleteMe
val longVal: Long = 123456789L

@DeleteMe
val intVal: Int = 123456

@DeleteMe
val <T: Any> T.memberVal: T get() = this
