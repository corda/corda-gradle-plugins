@file:Suppress("unused", "PackageDirectoryMismatch")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

sealed class SealedBaseClass

@DeleteMe
class UnwantedSubclass : SealedBaseClass()

class WantedSubclass : SealedBaseClass()
