@file:JvmName("PackageTemplate")
@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.metadataAs
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isFunction
import net.corda.gradle.jarfilter.matcher.isProperty
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.jvm.kotlin
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMembers
import kotlin.test.assertFailsWith

/**
 * These tests cannot actually "test" anything until Kotlin reflection
 * supports package metadata. Until then, we can only execute the code
 * paths to ensure they don't throw any exceptions.
 */
@TestInstance(PER_CLASS)
class MetaFixPackageTest {
    private companion object {
        private const val TEMPLATE_CLASS = "net.corda.gradle.jarfilter.PackageTemplate"
        private const val EMPTY_CLASS = "net.corda.gradle.jarfilter.EmptyPackage"
        private val logger: Logger = StdOutLogging(MetaFixPackageTest::class)
        private val staticVal = isProperty("templateVal", Long::class)
        private val staticVar = isProperty("templateVar", Int::class)
        private val staticFun = isFunction("templateFun", String::class)
    }

    private lateinit var sourceClass: Class<out Any>
    private lateinit var fixedClass: Class<out Any>

    @BeforeAll
    fun setup() {
        val emptyClass = Class.forName(EMPTY_CLASS)
        val bytecode = emptyClass.metadataAs(Class.forName(TEMPLATE_CLASS))
        sourceClass = bytecode.toClass(emptyClass, Any::class.java)
        fixedClass = bytecode.fixMetadata(logger, setOf(EMPTY_CLASS)).toClass(sourceClass, Any::class.java)
    }

    @Test
    fun testPackageFunction() {
        assertFailsWith<UnsupportedOperationException> { fixedClass.kotlin.declaredFunctions }
        //assertThat("templateFun() not found", sourceClass.kotlin.declaredFunctions, hasItem(staticFun))
        //assertThat("templateFun() still exists", fixedClass.kotlin.declaredFunctions, not(hasItem(staticFun)))
    }

    @Test
    fun testPackageVal() {
        assertFailsWith<UnsupportedOperationException> { fixedClass.kotlin.declaredMembers }
        //assertThat("templateVal not found", sourceClass.kotlin.declaredMembers, hasItem(staticVal))
        //assertThat("templateVal still exists", fixedClass.kotlin.declaredMembers, not(hasItem(staticVal)))
    }

    @Test
    fun testPackageVar() {
        assertFailsWith<UnsupportedOperationException> { fixedClass.kotlin.declaredMembers }
        //assertThat("templateVar not found", sourceClass.kotlin.declaredMembers, hasItem(staticVar))
        //assertThat("templateVar still exists", fixedClass.kotlin.declaredMembers, not(hasItem(staticVar)))
    }
}

internal fun templateFun(): String = MESSAGE
internal const val templateVal: Long = BIG_NUMBER
internal var templateVar: Int = NUMBER