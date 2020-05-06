package net.corda.plugins


import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConfigurationUtilsTest {

    @Test
    fun `check correct port value parsing`() {
        assertEquals(ConfigurationUtils.parsePort("localhost:10000"), 10000)
    }

    @Test
    fun `missing port value correctly identified in valid address`() {
        assertEquals(ConfigurationUtils.parsePort("localhost"), -1)
    }

    @Test
    fun `missing port value correctly identified in invalid address`() {
        assertEquals(ConfigurationUtils.parsePort("localhost!"), -1)
    }
}