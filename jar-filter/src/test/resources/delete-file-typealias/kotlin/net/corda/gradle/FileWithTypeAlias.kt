@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("FileWithTypeAlias")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

typealias FileWantedType = Long

@DeleteMe
typealias FileUnwantedType = (String) -> Boolean

val Any.FileUnwantedType: String get() = "<value>"
