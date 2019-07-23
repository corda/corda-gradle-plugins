@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

class HasFunctionWithDefaultParameter {
    @DeleteMe
    fun unwantedFun(str: String = "<default-value>"): String {
        return str
    }
}
