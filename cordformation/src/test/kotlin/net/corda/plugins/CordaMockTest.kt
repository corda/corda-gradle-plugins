package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CordaMockTest {
    @Test
    fun testEquality() {
        val mock = cordaMock<Project>()
        assertEquals(mock, mock)
        assertNotEquals(mock, cordaMock<Project>())
    }

    @Test
    fun testToString() {
        assertThat(cordaMock<Project>().toString())
            .isInstanceOf(String::class.java)
    }

    @Test
    fun testHashCode() {
        val mock = cordaMock<Project>()
        val hash = mock.hashCode()
        assertEquals(hash, mock.hashCode())
    }

    @Test
    fun testMockedFunctions() {
        val mockProject = cordaMock<Project>()
        assertNull(mockProject.name)
        assertNull(mockProject.path)
        assertEquals(0, mockProject.depth)
        mockProject.evaluationDependsOnChildren()
    }
}
