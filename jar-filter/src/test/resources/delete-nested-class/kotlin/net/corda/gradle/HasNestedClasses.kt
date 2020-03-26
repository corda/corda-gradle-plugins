@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

class HasNestedClasses {
    class OneToKeep

    @DeleteMe class OneToThrowAway
}
