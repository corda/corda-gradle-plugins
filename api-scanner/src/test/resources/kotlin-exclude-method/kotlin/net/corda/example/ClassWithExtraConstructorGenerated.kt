@file:Suppress("UNUSED")
package net.corda.example

class ClassWithExtraConstructorGenerated private constructor(private val val1: String, private val val2: String) {
    companion object {
        internal fun create(val1: String, val2: String): ClassWithExtraConstructorGenerated {
            return ClassWithExtraConstructorGenerated(val1, val2)
        }
    }
}