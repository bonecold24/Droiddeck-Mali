package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Directive #11 A4: environment table fixes.
 * - Vulkan API version parsed from vulkaninfo output.
 * - AVC denials filtered to the run window, split GPU-relevant vs other.
 */
class EnvironmentTableTest {

    @Test fun parsesHumanReadableApiVersion() {
        val text = """
            VkPhysicalDeviceProperties:
            ===========================
            	apiVersion     = 1.4.363 (0x40514b)
            	driverVersion  = 12345
        """.trimIndent()
        assertEquals("1.4.363", CompatTestExecutor.parseVulkanApiVersion(text))
    }

    @Test fun parsesJsonApiVersion() {
        val text = """{"apiVersion": "1.4.321", "driverVersion": 1}"""
        assertEquals("1.4.321", CompatTestExecutor.parseVulkanApiVersion(text))
    }

    @Test fun returnsNullWhenNoApiVersion() {
        assertNull(CompatTestExecutor.parseVulkanApiVersion("no version here"))
    }

    @Test fun avcFilterKeepsInWindowDenials() {
        // Run window: 2026-10-16 16:50:00 to 16:55:00 (PKT).
        // Use a fixed reference: construct timestamps relative to now.
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val mm = "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
        val dd = "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))
        val hh = "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY))
        val min = "%02d".format(cal.get(java.util.Calendar.MINUTE))
        // A denial from 2 minutes ago (inside a 5-min window).
        val recentMin = "%02d".format((cal.get(java.util.Calendar.MINUTE) - 2 + 60) % 60)
        val recentLine = "$mm-$dd $hh:$recentMin:00.123  1234  5678 W avc: denied { getattr } for pid=1 name=\"mali0\""
        // A denial from 2 hours ago (outside).
        val oldHh = "%02d".format((cal.get(java.util.Calendar.HOUR_OF_DAY) - 2 + 24) % 24)
        val oldLine = "$mm-$dd $oldHh:$min:00.123  1234  5678 W avc: denied { getattr } for pid=1 name=\"gpuinfo\""
        val text = "$recentLine\n$oldLine"
        val (relevant, other) = KbaseUapi.filterAvcByTime(
            text, now - 300_000L, now + 300_000L
        )
        // The recent mali denial is in-window and GPU-relevant.
        assertTrue(relevant.contains("mali0"))
        // The old gpuinfo denial is out-of-window and must not appear.
        assertTrue(!relevant.contains("gpuinfo") && !other.contains("gpuinfo"))
    }

    @Test fun avcSplitSeparatesGpuFromOther() {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val mm = "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
        val dd = "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))
        val hh = "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY))
        val min = "%02d".format(cal.get(java.util.Calendar.MINUTE))
        val gpuLine = "$mm-$dd $hh:$min:00.123  1  2 W avc: denied { ioctl } for pid=1 name=\"mali0\""
        val otherLine = "$mm-$dd $hh:$min:01.123  1  2 W avc: denied { read } for pid=1 name=\"version\""
        val (relevant, other) = KbaseUapi.filterAvcByTime(
            "$gpuLine\n$otherLine", now - 300_000L, now + 300_000L
        )
        assertTrue(relevant.contains("mali0"))
        assertTrue(!relevant.contains("version"))
        assertTrue(other.contains("version"))
        assertTrue(!other.contains("mali0"))
    }

    @Test fun avcEmptyReturnsEmpty() {
        val (r, o) = KbaseUapi.filterAvcByTime("", 0L, Long.MAX_VALUE)
        assertEquals("", r)
        assertEquals("", o)
    }
}
