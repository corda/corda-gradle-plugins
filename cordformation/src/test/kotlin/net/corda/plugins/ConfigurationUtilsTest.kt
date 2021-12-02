package net.corda.plugins

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

    @Test
    fun `parse port value when address has dashes`() {
        assertEquals(10002, ConfigurationUtils.parsePort("notary-service-org-unit:10002"))
    }
}
