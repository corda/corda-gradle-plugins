package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isFunction
import net.corda.gradle.unwanted.HasLong
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.jvm.kotlin
import kotlin.reflect.full.declaredFunctions

class MetaFixFunctionTest {
    private companion object {
        private val logger: Logger = StdOutLogging(MetaFixFunctionTest::class)
        private val longData = isFunction("longData", Long::class)
        private val unwantedFun = isFunction("unwantedFun", String::class, String::class)
    }

    @Test
    fun testFunctionRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithFunction, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithFunction, HasLong>()

        // Check that the unwanted function has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.getDeclaredConstructor().newInstance()
        assertEquals(BIG_NUMBER, sourceObj.longData())
        with(sourceClass.kotlin.declaredFunctions) {
            assertThat("unwantedFun(String) not found", this, hasItem(unwantedFun))
            assertThat("longData not found", this, hasItem(longData))
        }

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithFunction::class)).toClass<WithFunction, HasLong>()
        val fixedObj = fixedClass.getDeclaredConstructor().newInstance()
        assertEquals(BIG_NUMBER, fixedObj.longData())
        with(fixedClass.kotlin.declaredFunctions) {
            assertThat("unwantedFun(String) still exists", this, not(hasItem(unwantedFun)))
            assertThat("longData not found", this, hasItem(longData))
        }
    }

    class MetadataTemplate : HasLong {
        override fun longData(): Long = 0
        @Suppress("UNUSED") fun unwantedFun(str: String): String = "UNWANTED[$str]"
    }
}

class WithFunction : HasLong {
    override fun longData(): Long = BIG_NUMBER
}
