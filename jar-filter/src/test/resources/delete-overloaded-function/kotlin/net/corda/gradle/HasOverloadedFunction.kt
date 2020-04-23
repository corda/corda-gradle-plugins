@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

class HasOverloadedFunction {
    @DeleteMe
    @JvmOverloads
    fun stringData(str: String = "<default-value>") = str

    @JvmOverloads
    fun stringData(number: Int, str: String = "<default-value>") = "$number: $str"
}

class HasOverloadWithLambda {
    @DeleteMe
    @JvmOverloads
    fun lambdaData(str: String, action: (String) -> String = { s -> "[$s]" }) = action(str)

    @JvmOverloads
    fun lambdaData(value: Int, action: (String) -> String = { s -> "($s)" }) = action(value.toString())
}