package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.*

class MethodElementTest {
    private companion object {
        private const val DESCRIPTOR = "(Z)Ljava/lang/String;"
        private const val CLASS_DESCRIPTOR = "Ljava/util/List;"
    }

    @Test
    fun testMethodsMatchByNameAndDescriptor() {
        val elt = MethodElement(
            name = "getThing",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC or ACC_ABSTRACT or ACC_FINAL
        )
        assertEquals(MethodElement(name="getThing", descriptor=DESCRIPTOR), elt)
        assertNotEquals(MethodElement(name="getOther", descriptor=DESCRIPTOR), elt)
        assertNotEquals(MethodElement(name="getThing", descriptor="()J"), elt)
    }

    @Test
    fun testBasicMethodVisibleName() {
        val elt = MethodElement(
            name = "getThing",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertEquals("getThing", elt.visibleName)
    }

    @Test
    fun testMethodVisibleNameWithSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertEquals("getThing", elt.visibleName)
    }

    @Test
    fun testSyntheticMethodSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC or ACC_SYNTHETIC
        )
        assertTrue(elt.isKotlinSynthetic("extra"))
        assertFalse(elt.isKotlinSynthetic("something"))
        assertTrue(elt.isKotlinSynthetic("extra", "something"))
    }

    @Test
    fun testPublicMethodSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertFalse(elt.isKotlinSynthetic("extra"))
    }

    @Test
    fun testMethodDoesNotExpire() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertFalse(elt.isDummy)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
    }

    @Test
    fun testArtificialMethodDoesExpire() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR
        )
        assertTrue(elt.isDummy)
        assertFalse(elt.isExpired)
        assertTrue(elt.isExpired)
        assertTrue(elt.isExpired)
    }

    @Test
    fun testDefaultMethodForMethod() {
        val elt = MethodElement(
            name = "getThing",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        val defaultElt = elt.asKotlinDefaultFunction(CLASS_DESCRIPTOR) ?: fail("Cannot be null")
        assertEquals("getThing\$default", defaultElt.name)
        assertEquals("(${CLASS_DESCRIPTOR}ZILjava/lang/Object;)Ljava/lang/String;", defaultElt.descriptor)
        assertTrue(defaultElt.isDummy)
    }

    @Test
    fun testDefaultMethodForDefaultMethod() {
        val elt = MethodElement(
            name = "getThing\$default",
            descriptor = "(ZILjava/lang/Object;)Ljava/lang/String;",
            access = ACC_PUBLIC or ACC_SYNTHETIC
        )
        val defaultElt = elt.asKotlinDefaultFunction(CLASS_DESCRIPTOR) ?: fail("Cannot be null")
        assertSame(elt, defaultElt)
    }

    @Test
    fun testDefaultForInvalidMethod() {
        val elt = MethodElement(
            name = "thing",
            descriptor = "Ljava/lang/String;"
        )
        assertNull(elt.asKotlinDefaultFunction(CLASS_DESCRIPTOR))
    }
}