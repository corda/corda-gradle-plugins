@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DecorateMe
import net.corda.gradle.jarfilter.DeleteMe

class DeleteAmbiguousValProperty {
    @DeleteMe
    @get:JvmName("nameIntToInt")
    val Map<Int, Int>.name: String get() = "int-to-int"

    @DecorateMe
    @get:JvmName("nameIntToByte")
    val Map<Int, Byte>.name: String get() = "int-to-byte"

    @DecorateMe
    @get:JvmName("nameString")
    val Collection<String>.name: String get() = "string"

    @DeleteMe
    @get:JvmName("nameLong")
    val Collection<Long>.name: String get() = "long"
}
