@file:Suppress("UNUSED")
package net.corda.example

class HasJvmStaticField {
    companion object {
        @JvmStatic
        val stringValue = "Hello World"
    }
}
