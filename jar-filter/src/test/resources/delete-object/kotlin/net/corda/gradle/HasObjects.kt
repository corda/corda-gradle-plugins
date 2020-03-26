@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("HasObjects")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedFun
import net.corda.gradle.unwanted.HasUnwantedVal

@DeleteMe
val unwantedObj = object : HasUnwantedFun {
    override fun unwantedFun(str: String): String = str
}

@DeleteMe
val unwantedGetObj get() = object : HasUnwantedFun {
    override fun unwantedFun(str: String): String = str
}

@field:DeleteMe
@JvmField
val unwantedFieldObj = object : HasUnwantedFun {
    override fun unwantedFun(str: String): String = str
}

@DeleteMe
fun unwantedFun(): String {
    val obj = object : HasUnwantedVal {
        override val unwantedVal: String = "<default-value>"
    }
    return obj.unwantedVal
}
