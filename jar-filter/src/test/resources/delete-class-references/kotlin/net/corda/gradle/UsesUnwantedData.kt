@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasStringVal

class UsesUnwantedData {
    companion object {
        @JvmField
        val jvmCompanionVal = UnwantedData("jvmCompanionVal")

        @JvmField
        var jvmCompanionVar = UnwantedData("jvmCompanionVar")
    }

    private lateinit var unwantedField: UnwantedData

    @JvmField
    var jvmUnwantedVar = UnwantedData("jvmVar")

    @JvmField
    val jvmUnwantedVal = UnwantedData("jvmVal")

    var unwantedVar: UnwantedData
        get() = throw UnsupportedOperationException("VAR-GETTER")
        set(value) = throw UnsupportedOperationException("VAR-SETTER $value")

    val unwantedVal: UnwantedData
        get() = throw UnsupportedOperationException("VAR-GETTER")

    fun input(input: UnwantedData) {
        throw UnsupportedOperationException("INPUT: $input")
    }

    fun output(): UnwantedData {
        throw UnsupportedOperationException("OUTPUT")
    }
}

@DeleteMe
data class UnwantedData(override val stringVal: String) : HasStringVal
