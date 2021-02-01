package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isProperty
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsIterableContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties

class MetaFixFieldValPropertyTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixFieldValPropertyTest::class)
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val wantedVal = isProperty("wantedVal", Int::class)
    }

    @Test
    fun testFieldPropertyRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithFieldValProperty, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithFieldValProperty, Any>()

        // Check that the unwanted property has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.getDeclaredConstructor().newInstance()
        assertEquals(NUMBER, sourceClass.getField("wantedVal").get(sourceObj))
        assertThat("unwantedVal not found", sourceClass.kotlin.declaredMemberProperties, hasItem(unwantedVal))
        assertThat("wantedVal not found", sourceClass.kotlin.declaredMemberProperties, hasItem(wantedVal))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithFieldValProperty::class)).toClass<WithFieldValProperty, Any>()
        val fixedObj = fixedClass.getDeclaredConstructor().newInstance()
        assertEquals(NUMBER, fixedClass.getField("wantedVal").get(fixedObj))
        assertThat("unwantedVal still exists", fixedClass.kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
        assertThat("wantedVal not found", fixedClass.kotlin.declaredMemberProperties, hasItem(wantedVal))
    }

    class MetadataTemplate {
        @JvmField
        @Suppress("unused")
        val wantedVal: Int = 0

        @JvmField
        val unwantedVal: String = "UNWANTED"
    }
}

class WithFieldValProperty {
    @JvmField
    @Suppress("unused")
    val wantedVal: Int = NUMBER
}
