package net.corda.gradle

import net.corda.gradle.unwanted.HasAll
import net.corda.gradle.unwanted.HasAllVal
import net.corda.gradle.unwanted.HasAllVar

class ExampleKotlin : HasAll {
    class Nested(
         override val stringVal: String,
         override val longVal: Long,
         override val intVal: Int
    ) : HasAllVal

    inner class Inner(
        override var stringVar: String,
        override var longVar: Long,
        override var intVar: Int
    ) : HasAllVar

    override fun stringData(): String = "Hello World!"
    override fun longData(): Long = Long.MAX_VALUE
    override fun intData(): Int = Int.MIN_VALUE
}
