package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-runnable tests for the pure parts of [MaliDiagnostics]. */
class MaliDiagnosticsTest {

    @Test
    fun formatGpuIdHex() {
        assertEquals("0xC8700000", MaliDiagnostics.formatGpuId(0xC8700000L))
        assertEquals("unknown", MaliDiagnostics.formatGpuId(null))
    }

    @Test
    fun summarizeProvenanceTakesHeadLines() {
        val text = "PanVK kbase RC3\nBuilt 2026-10-14\n\nProfile g720-v12-csf\nLine four\nLine five\n"
        val summary = MaliDiagnostics.summarizeProvenance(text, maxLines = 3)
        assertTrue(summary.contains("PanVK kbase RC3"))
        assertTrue(summary.contains("Profile g720-v12-csf"))
        assertTrue("summary: $summary", !summary.contains("Line four"))
    }

    @Test
    fun summarizeProvenanceEmpty() {
        assertEquals("not recorded", MaliDiagnostics.summarizeProvenance(""))
    }

    @Test
    fun rowsAreOrderedAndComplete() {
        val report = MaliDiagnosticsReport(
            driverName = "PanVK Kbase (mission local build)",
            driverVersion = "0.1.0-rc3-universal+local1",
            abi = "android-aarch64-bionic",
            vulkanApiVersion = "1.4.363",
            extensionCount = "not measured on this device",
            kbaseUapi = "1.21",
            gpuId = "0xC8700000",
            gpuName = "Mali-G720",
            fallbackPosition = "PanVK-kbase (preferred)",
            fallbackReason = "kbase UAPI 1.21 is a tested kernel interface.",
            provenanceSummary = "PanVK kbase RC3",
            notes = listOf("a note"),
        )
        val rows = report.rows()
        val labels = rows.map { it.first }
        assertEquals(
            listOf("Driver", "ABI", "Vulkan API", "Extensions", "GPU", "kbase UAPI", "Fallback chain", "Chain reason", "Provenance", "Note"),
            labels,
        )
        assertTrue(rows[0].second.contains("0.1.0-rc3-universal+local1"))
        assertTrue(rows[4].second.contains("0xC8700000"))
        assertTrue(rows[7].second.contains("tested kernel interface"))
    }
}
