package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-runnable tests for [MaliFallbackChain.select] given fixture device profiles:
 * PanVK-kbase (preferred) -> stock Mali ICD + spoof -> diagnostic report.
 * Software GL is only allowed when no real GPU was detected at all (lab).
 */
class MaliFallbackChainTest {

    private fun profile(
        gpuId: Long? = 0xC8700000L,
        kbase: Boolean = true,
        readable: Boolean = true,
        uapi: String? = "1.21",
    ) = MaliDeviceProfile(
        gpuId = gpuId, gpuName = "Mali-G720",
        kbaseNodePresent = kbase, kbaseNodeReadable = readable,
        kbaseUapi = uapi, heaps = emptyList(), notes = emptyList(),
    )

    @Test
    fun g720MatchSelectsPanvk() {
        val d = MaliFallbackChain.select(profile(), panvkDriverId = "drv1", stockMaliPresent = true)
        assertEquals(MaliFallbackPosition.PANVK_KBASE, d.position)
        assertEquals("drv1", d.driverId)
        assertFalse(d.softwareGlAllowed)
        assertTrue(d.reason.contains("1.21"))
    }

    @Test
    fun unknownUapiStillTriesPanvkWithNote() {
        // The UAPI is not exposed in sysfs on most retail kernels: trying is correct,
        // silently assuming is not - the reason must say so.
        val d = MaliFallbackChain.select(profile(uapi = null), panvkDriverId = "drv1", stockMaliPresent = true)
        assertEquals(MaliFallbackPosition.PANVK_KBASE, d.position)
        assertTrue("reason: ${d.reason}", d.reason.contains("not readable", ignoreCase = true))
        assertFalse(d.softwareGlAllowed)
    }

    @Test
    fun kbaseMismatchFallsThroughToStockSpoof() {
        val d = MaliFallbackChain.select(profile(uapi = "1.19"), panvkDriverId = "drv1", stockMaliPresent = true)
        assertEquals(MaliFallbackPosition.STOCK_MALI_SPOOF, d.position)
        assertNull(d.driverId)
        assertTrue("reason: ${d.reason}", d.reason.contains("1.19") && d.reason.contains("differs"))
        assertFalse(d.softwareGlAllowed)
    }

    @Test
    fun kbaseMismatchWithoutStockReachesDiagnostic() {
        val d = MaliFallbackChain.select(profile(uapi = "1.19"), panvkDriverId = "drv1", stockMaliPresent = false)
        assertEquals(MaliFallbackPosition.DIAGNOSTIC_REPORT, d.position)
        assertFalse(d.softwareGlAllowed)
    }

    @Test
    fun unknownMaliUsesStockSpoof() {
        val d = MaliFallbackChain.select(profile(gpuId = 0xB6150000L), panvkDriverId = null, stockMaliPresent = true)
        assertEquals(MaliFallbackPosition.STOCK_MALI_SPOOF, d.position)
        assertFalse(d.softwareGlAllowed)
    }

    @Test
    fun panvkWithoutKbaseNodeFallsThrough() {
        // A driver is imported but /dev/mali0 is not there: PanVK cannot open the GPU.
        val d = MaliFallbackChain.select(profile(kbase = false), panvkDriverId = "drv1", stockMaliPresent = true)
        assertEquals(MaliFallbackPosition.STOCK_MALI_SPOOF, d.position)
        assertFalse(d.softwareGlAllowed)
    }

    @Test
    fun noGpuAtAllAllowsSoftwareGlInLab() {
        val d = MaliFallbackChain.select(profile = null, panvkDriverId = null, stockMaliPresent = false)
        assertEquals(MaliFallbackPosition.DIAGNOSTIC_REPORT, d.position)
        assertTrue(d.softwareGlAllowed)
    }

    @Test
    fun realDeviceNeverFallsBackToSoftwareSilently() {
        // kbase present but no driver anywhere: diagnostic, and software GL stays blocked.
        val d = MaliFallbackChain.select(profile(uapi = null), panvkDriverId = null, stockMaliPresent = false)
        assertEquals(MaliFallbackPosition.DIAGNOSTIC_REPORT, d.position)
        assertFalse("software GL must stay blocked on a real Mali", d.softwareGlAllowed)
    }

    @Test
    fun emptyProfileCountsAsNoGpu() {
        val empty = MaliDeviceProfile(null, "Mali (unknown model)", false, false, null, emptyList(), listOf("n/a"))
        val d = MaliFallbackChain.select(profile = empty, panvkDriverId = null, stockMaliPresent = false)
        assertEquals(MaliFallbackPosition.DIAGNOSTIC_REPORT, d.position)
        assertTrue(d.softwareGlAllowed)
    }
}
