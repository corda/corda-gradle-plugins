package net.corda.plugins


import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConfigurationUtilsTest {

    @Test
    fun `check correct port value parsing`() {
        assertEquals(10000, ConfigurationUtils.parsePort("localhost:10000"))
    }

    @Test
    fun `missing port value correctly identified in valid address`() {
        assertEquals(-1, ConfigurationUtils.parsePort("localhost"))
    }

    @Test
    fun `missing port value correctly identified in invalid address`() {
        assertEquals(-1, ConfigurationUtils.parsePort("localhost!"))
    }
}