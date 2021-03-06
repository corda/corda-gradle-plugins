package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.bytecode
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isKonstructor
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.junit.jupiter.api.Test

class MetaFixAnnotationTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixAnnotationTest::class)
        private val defaultCon = isKonstructor(
            returnType = SimpleAnnotation::class
        )
        private val valueCon = isKonstructor(
            returnType = AnnotationWithValue::class,
            parameters = *arrayOf(String::class)
        )
    }

    @Test
    fun testSimpleAnnotation() {
        val sourceClass = SimpleAnnotation::class.java
        assertThat("<init>() not found", sourceClass.kotlin.constructors, hasItem(defaultCon))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = sourceClass.bytecode.fixMetadata(logger, pathsOf(SimpleAnnotation::class))
                 .toClass<SimpleAnnotation, Any>()
        assertThat("<init>() not found", fixedClass.kotlin.constructors, hasItem(defaultCon))
    }

    @Test
    fun testAnnotationWithValue() {
        val sourceClass = AnnotationWithValue::class.java
        assertThat("<init>(String) not found", sourceClass.kotlin.constructors, hasItem(valueCon))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = sourceClass.bytecode.fixMetadata(logger, pathsOf(AnnotationWithValue::class))
                .toClass<AnnotationWithValue, Any>()
        assertThat("<init>(String) not found", fixedClass.kotlin.constructors, hasItem(valueCon))
    }
}

annotation class AnnotationWithValue(val str: String)
annotation class SimpleAnnotation
