@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

sealed class SealedClass {
    class Wanted : SealedClass()

    @DeleteMe class Unwanted : SealedClass()
}
