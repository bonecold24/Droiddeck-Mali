package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-runnable tests for [DriverVersion] ordering. */
class DriverVersionTest {

    private fun cmp(a: String, b: String) = DriverVersion.compare(a, b).let { if (it < 0) -1 else if (it > 0) 1 else 0 }

    @Test
    fun numericPartsCompareNumerically() {
        assertEquals(-1, cmp("0.1.0", "0.1.10"))
        assertEquals(1, cmp("0.2.0", "0.1.9"))
        assertEquals(0, cmp("1.4.363", "1.4.363"))
        assertEquals(1, cmp("1.4.363", "1.4.36"))
    }

    @Test
    fun releaseBeatsPrerelease() {
        assertEquals(1, cmp("0.1.0", "0.1.0-rc3"))
        assertEquals(-1, cmp("0.1.0-beta.2", "0.1.0"))
    }

    @Test
    fun prereleaseQualifiersOrder() {
        assertEquals(-1, cmp("0.1.0-alpha.1", "0.1.0-beta.1"))
        assertEquals(-1, cmp("0.1.0-beta.1", "0.1.0-rc3"))
        assertEquals(1, cmp("0.1.0-rc4", "0.1.0-rc3"))
        assertEquals(-1, cmp("0.1.0-rc3-universal", "0.1.0-rc4"))
    }

    @Test
    fun buildMetadataIsIgnoredForPrecedence() {
        assertEquals(0, cmp("0.1.0-rc3-universal+local1", "0.1.0-rc3-universal+local2"))
        assertEquals(0, cmp("1.0.0+build.1", "1.0.0"))
    }

    @Test
    fun missionPackageVersionsOrder() {
        assertTrue(DriverVersion.isNewer("0.1.0-rc3-universal+local2", "0.1.0-rc3-universal+local1") == false) // build metadata ignored
        assertTrue(DriverVersion.isNewer("0.1.0", "0.1.0-rc3-universal+local1"))
        assertTrue(DriverVersion.isNewer("0.2.0-rc1", "0.1.0"))
    }

    @Test
    fun junkVersionsDoNotThrow() {
        // Must never throw: versions come from zip metadata the user supplies.
        DriverVersion.compare("", "1.0")
        DriverVersion.compare("not-a-version", "also-not")
        DriverVersion.compare("1.0", "")
    }
}
