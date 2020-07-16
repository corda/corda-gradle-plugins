package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.*
import org.gradle.api.logging.Logger
import org.hamcrest.MatcherAssert.*
import org.hamcrest.core.IsIterableContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties

class MetaFixFieldVarPropertyTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixVarPropertyTest::class)
        private val unwantedVar = isProperty("unwantedVar", String::class)
        private val wantedVar = isProperty("wantedVar", Int::class)
    }

    @Test
    fun testPropertyRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithFieldVarProperty, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithFieldVarProperty, Any>()

        // Check that the unwanted property has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.getDeclaredConstructor().newInstance()
        assertEquals(NUMBER, sourceClass.getField("wantedVar").get(sourceObj))
        assertThat("unwantedVar not found", sourceClass.kotlin.declaredMemberProperties, hasItem(unwantedVar))
        assertThat("wantedVar not found", sourceClass.kotlin.declaredMemberProperties, hasItem(wantedVar))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithFieldVarProperty::class)).toClass<WithFieldVarProperty, Any>()
        val fixedObj = fixedClass.getDeclaredConstructor().newInstance()
        assertEquals(NUMBER, fixedClass.getField("wantedVar").get(fixedObj))
        assertThat("unwantedVar still exists", fixedClass.kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
        assertThat("wantedVar not found", fixedClass.kotlin.declaredMemberProperties, hasItem(wantedVar))
    }

    class MetadataTemplate {
        @Suppress("unused")
        @JvmField
        var wantedVar: Int = 0

        @Suppress("unused")
        @JvmField
        var unwantedVar: String = "UNWANTED"
    }
}

class WithFieldVarProperty {
    @Suppress("unused")
    @JvmField
    var wantedVar: Int = NUMBER
}
