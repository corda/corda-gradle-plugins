@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("HasMultiData")
@file:JvmMultifileClass
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
fun longToDelete(data: Long): Long = data
