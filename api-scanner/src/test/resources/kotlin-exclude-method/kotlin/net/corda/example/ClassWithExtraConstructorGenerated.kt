@file:Suppress("UNUSED")
package net.corda.example

class ClassWithExtraConstructorGenerated private constructor(val val1: String, val val2: String) {
    companion object {
        internal fun create(val1: String, val2: String): ClassWithExtraConstructorGenerated {
            return ClassWithExtraConstructorGenerated(val1, val2)
        }
    }
}