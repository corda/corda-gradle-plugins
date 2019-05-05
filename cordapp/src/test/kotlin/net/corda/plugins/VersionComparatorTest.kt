package net.corda.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionComparatorTest {
    @Test
    fun testVersions() {
        assertThat(Utils.compareVersions("3.0.1", "3.0.0")).isGreaterThan(0)
        assertThat(Utils.compareVersions("2.2.8", "2.2.89")).isLessThan(0)
        assertThat(Utils.compareVersions("5.8.9", "5.8.9")).isEqualTo(0)
        assertThat(Utils.compareVersions("5.8.9", "5.8")).isGreaterThan(0)
        assertThat(Utils.compareVersions("4.1", "4.1.1")).isLessThan(0)
        assertThat(Utils.compareVersions("4.1", "4.1.0.0")).isEqualTo(0)
        assertThat(Utils.compareVersions("4.1-rc-1", "4.1.0.0")).isLessThan(0)
        assertThat(Utils.compareVersions("4.1.0.0", "4.1-rc-1")).isGreaterThan(0)
        assertThat(Utils.compareVersions("4.1-rc-1", "4.1-rc-1")).isEqualTo(0)
        assertThat(Utils.compareVersions("4.0", "4.1-rc-1")).isLessThan(0)
        assertThat(Utils.compareVersions("4-0.1", "4.0-1")).isEqualTo(0)
        assertThat(Utils.compareVersions("4-0.2", "4.0-1")).isGreaterThan(0)
        assertThat(Utils.compareVersions("4-0.2", "4.0-2.1")).isLessThan(0)
    }
}