package net.corda.gradle.jarfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes.ACC_PUBLIC


class FieldElementTest {
    private companion object {
        private const val DESCRIPTOR = "Ljava.lang.String;"
    }

    @Test
    fun testFieldsMatchByNameOnly() {
        val elt = FieldElement(name = "fieldName", descriptor = DESCRIPTOR)
        assertEquals(FieldElement(name = "fieldName", descriptor = "?"), elt)
    }

    @Test
    fun testFieldWithAccessFlagsDoesNotExpire() {
        val elt = FieldElement(name = "fieldName", descriptor = DESCRIPTOR, access = ACC_PUBLIC)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
    }

    @Test
    fun testDummyFieldDoesExpire() {
        val elt = FieldElement(name = "fieldName", descriptor = "?")
        assertFalse(elt.isExpired)
        assertTrue(elt.isExpired)
        assertTrue(elt.isExpired)
    }
}