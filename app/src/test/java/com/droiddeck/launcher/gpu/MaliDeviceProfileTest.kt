package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-runnable tests for [MaliDeviceProfile.parse] from fixture sysfs dumps.
 * No real hardware: the map stands in for /dev/mali0 + sysfs + probe results.
 */
class MaliDeviceProfileTest {

    /** Fixture mirroring the 14T Pro: Dimensity 9300+, Immortalis-G720 MC12. */
    private fun g720Files() = mapOf(
        "/dev/mali0" to "",
        "/dev/mali0:access" to "rw",
        "/sys/class/misc/mali0/device/gpuinfo" to
            "Mali-G720-Immortalis MC12\nGPU ID: 0xC8700000\nCores: 12\n",
        "kbase_uapi" to "1.21",
        "vulkan_heaps" to "device-local|8589934592|1\nunified|4294967296|0\n",
    )

    @Test
    fun g720ProfileParses() {
        val p = MaliDeviceProfile.parse(g720Files())
        assertEquals(0xC8700000L, p.gpuId)
        assertTrue(p.isG720)
        assertTrue(p.hasKnownGpu)
        assertTrue(p.gpuName.contains("Mali-G720"))
        assertTrue(p.kbaseNodePresent)
        assertTrue(p.kbaseNodeReadable)
        assertEquals("1.21", p.kbaseUapi)
        assertEquals(2, p.heaps.size)
        assertEquals("device-local", p.heaps[0].name)
        assertEquals(8589934592L, p.heaps[0].sizeBytes)
        assertTrue(p.heaps[0].deviceLocal)
        assertFalse(p.heaps[1].deviceLocal)
    }

    @Test
    fun unknownMaliIsNotG720() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/dev/mali0" to "",
            "/dev/mali0:access" to "r",
            "/sys/class/misc/mali0/device/gpuinfo" to "Mali-G615 MC6\nGPU ID: 0xB6150000\n",
        ))
        assertEquals(0xB6150000L, p.gpuId)
        assertTrue(p.hasKnownGpu)
        assertFalse(p.isG720)
        // UAPI not exposed in sysfs: graceful null + note, never a crash.
        assertNull(p.kbaseUapi)
        assertTrue(p.notes.any { it.contains("UAPI") })
    }

    @Test
    fun unlabeledHexIdIsPickedUp() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/sys/devices/platform/mali/gpuinfo" to "Mali-G720 r1p0 0xc8700000",
        ))
        assertEquals(0xC8700000L, p.gpuId)
        assertTrue(p.isG720)
    }

    @Test
    fun emptyDumpDegradesGracefully() {
        val p = MaliDeviceProfile.parse(emptyMap())
        assertNull(p.gpuId)
        assertFalse(p.hasKnownGpu)
        assertFalse(p.isG720)
        assertFalse(p.kbaseNodePresent)
        assertNull(p.kbaseUapi)
        assertTrue(p.heaps.isEmpty())
        assertTrue("expected degradation notes, got ${p.notes}", p.notes.isNotEmpty())
    }

    @Test
    fun garbageGpuinfoDoesNotThrow() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/dev/mali0" to "",
            "/sys/class/misc/mali0/device/gpuinfo" to "hello world\nno gpu here\n",
        ))
        assertNull(p.gpuId)
        assertFalse(p.hasKnownGpu)
        assertTrue(p.notes.any { it.contains("GPU id", ignoreCase = true) })
    }

    @Test
    fun missingKbaseNodeIsNoted() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/sys/class/misc/mali0/device/gpuinfo" to "Mali-G720\nGPU ID: 0xC8700000\n",
        ))
        assertFalse(p.kbaseNodePresent)
        assertTrue(p.notes.any { it.contains("/dev/mali0") })
        // The GPU id is still parsed: sysfs saw the GPU even without the node.
        assertTrue(p.isG720)
    }

    @Test
    fun uapiFallsBackToUevent() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/dev/mali0" to "",
            "/sys/class/misc/mali0/device/uevent" to "MAJOR=10\nMINOR=62\nDRIVER=mali\nVERSION=1.21\n",
        ))
        assertEquals("1.21", p.kbaseUapi)
    }

    @Test
    fun glRendererIsAFallbackNameSource() {
        val p = MaliDeviceProfile.parse(mapOf(
            "/dev/mali0" to "",
            "gl_renderer" to "Mali-G720 MC12",
        ))
        assertTrue(p.gpuName.contains("Mali-G720"))
        assertNull(p.gpuId)
    }

    @Test
    fun detectNeverThrowsOnThisHost() {
        // On a non-Mali host this just reports "no kbase here" - the point is it
        // must not throw anywhere (graceful degradation, item c).
        val p = MaliDeviceProfile.detect()
        assertTrue(p.notes.isNotEmpty() || !p.kbaseNodePresent)
    }
}
