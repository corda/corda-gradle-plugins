@file:Suppress("unused", "PackageDirectoryMismatch")
@file:JvmName("StaticFunctionsToDelete")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
fun unwantedStringToDelete(value: String): String = value

@DeleteMe
fun unwantedIntToDelete(value: Int): Int = value

@DeleteMe
fun unwantedLongToDelete(value: Long): Long = value

@DeleteMe
inline fun <reified T : Any> String.unwantedInlineToDelete(type: Class<T> = T::class.java): T {
    return type.getDeclaredConstructor(javaClass).newInstance("<default-value>") as T
}
