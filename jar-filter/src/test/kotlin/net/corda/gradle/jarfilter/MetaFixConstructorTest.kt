package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isKonstructor
import net.corda.gradle.unwanted.HasLong
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.jvm.kotlin

class MetaFixConstructorTest {
    private companion object {
        private val logger: Logger = StdOutLogging(MetaFixConstructorTest::class)
        private val unwantedCon = isKonstructor(WithConstructor::class, Int::class, Long::class)
        private val wantedCon = isKonstructor(WithConstructor::class, Long::class)
    }

    @Test
    fun testConstructorRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithConstructor, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithConstructor, HasLong>()

        // Check that the unwanted constructor has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
        assertEquals(BIG_NUMBER, sourceObj.longData())
        with(sourceClass.kotlin.constructors) {
            assertThat("<init>(Int,Long) not found", this, hasItem(unwantedCon))
            assertThat("<init>(Long) not found", this, hasItem(wantedCon))
        }

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithConstructor::class)).toClass<WithConstructor, HasLong>()
        val fixedObj = fixedClass.getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
        assertEquals(BIG_NUMBER, fixedObj.longData())
        with(fixedClass.kotlin.constructors) {
            assertThat("<init>(Int,Long) still exists", this, not(hasItem(unwantedCon)))
            assertThat("<init>(Long) not found", this, hasItem(wantedCon))
        }
    }

    class MetadataTemplate(private val longData: Long) : HasLong {
        @Suppress("UNUSED_PARAMETER", "UNUSED")
        constructor(intData: Int, longData: Long) : this(longData)
        override fun longData(): Long = longData
    }
}

class WithConstructor(private val longData: Long) : HasLong {
    override fun longData(): Long = longData
}
