package net.corda.gradle.jarfilter

import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

@DisabledIfSystemProperty(named = "test.kotlin.api", matches = "1\\.[23]")
@Target(CLASS, FUNCTION)
@Retention(RUNTIME)
@MustBeDocumented
annotation class RequiresKotlin14
