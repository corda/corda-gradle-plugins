package net.corda.plugins

import org.junit.Test

class VersionComparatorTest {
    @Test
    fun test() {
        assert(Utils.compareVersions("3.0.1", "3.0.0") > 0)
        assert(Utils.compareVersions("2.2.8", "2.2.89") < 0)
        assert(Utils.compareVersions("5.8.9", "5.8.9") == 0)
        assert(Utils.compareVersions("5.8.9", "5.8") > 0)
        assert(Utils.compareVersions("4.1", "4.1.1") < 0)
        assert(Utils.compareVersions("4.1", "4.1.0.0") == 0)
        assert(Utils.compareVersions("4.1-rc-1", "4.1.0.0") < 0)
        assert(Utils.compareVersions( "4.1.0.0", "4.1-rc-1") > 0)
        assert(Utils.compareVersions("4.1-rc-1", "4.1-rc-1") == 0)
        assert(Utils.compareVersions("4.0", "4.1-rc-1") < 0)
        assert(Utils.compareVersions("4-0.1", "4.0-1") == 0)
        assert(Utils.compareVersions("4-0.2", "4.0-1") > 0)
        assert(Utils.compareVersions("4-0.2", "4.0-2.1") < 0)
    }
}