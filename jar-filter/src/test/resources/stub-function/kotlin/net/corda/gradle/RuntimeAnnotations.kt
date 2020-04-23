@file:Suppress("PackageDirectoryMismatch")
package net.corda.gradle

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class Parameter
