package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class PerfPresetsTest {
    @Test fun presetsMapToConcrete9300PlusDefaults() {
        val battery = PerfPresets.for9300Plus(PerfPresets.Preset.BATTERY)
        assertEquals("0,1,2,3", battery.cpuPinning)
        assertEquals(30, battery.frameLimiterFps)
        assertEquals("", battery.fexPresetId)

        val balanced = PerfPresets.for9300Plus(PerfPresets.Preset.BALANCED)
        assertEquals("4,5,6", balanced.cpuPinning)
        assertEquals(60, balanced.frameLimiterFps)
        assertEquals("", balanced.fexPresetId)

        val performance = PerfPresets.for9300Plus(PerfPresets.Preset.PERFORMANCE)
        assertEquals("4,5,6,7", performance.cpuPinning)
        assertEquals(120, performance.frameLimiterFps)
        assertEquals("PERFORMANCE", performance.fexPresetId)
    }

    @Test fun presetIdsAreStable() {
        assertEquals(PerfPresets.Preset.BATTERY, PerfPresets.Preset.byId("battery"))
        assertEquals(PerfPresets.Preset.BALANCED, PerfPresets.Preset.byId("balanced"))
        assertEquals(PerfPresets.Preset.BALANCED, PerfPresets.Preset.byId("nonsense"))
        assertEquals(PerfPresets.Preset.BALANCED, PerfPresets.Preset.byId(null))
    }

    @Test fun frameLimiterEnvTargetsTheActiveLayer() {
        // DXVK/DXVK-Sarek honor dxvk.maxFrameRate; VKD3D-Proton honors VKD3D_FRAME_RATE.
        val dxvk = PerfPresets.frameLimiterEnv(60, ProtonSmartDefaults.TranslationLayer.DXVK_STOCK)
        assertEquals("dxvk.maxFrameRate = 60", dxvk["DXVK_CONFIG"])
        assertNull(dxvk["VKD3D_FRAME_RATE"])

        val vkd3d = PerfPresets.frameLimiterEnv(60, ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON)
        assertEquals("60", vkd3d["VKD3D_FRAME_RATE"])
        assertNull(vkd3d["DXVK_CONFIG"])

        val sarek = PerfPresets.frameLimiterEnv(30, ProtonSmartDefaults.TranslationLayer.DXVK_SAREK)
        assertEquals("dxvk.maxFrameRate = 30", sarek["DXVK_CONFIG"])
    }

    @Test fun limiterMergesWithExistingDxvkConfig() {
        val merged = PerfPresets.frameLimiterEnv(
            60, ProtonSmartDefaults.TranslationLayer.DXVK_STOCK, existingDxvkConfig = "dxvk.enableAsync = true")
        assertEquals("dxvk.enableAsync = true\ndxvk.maxFrameRate = 60", merged["DXVK_CONFIG"])
    }

    @Test fun nullLimiterDisablesLimiting() {
        assertTrue(PerfPresets.frameLimiterEnv(null, ProtonSmartDefaults.TranslationLayer.DXVK_STOCK).isEmpty())
    }

    @Test fun limiterRangeIsSane() {
        assertTrue(PerfPresets.validFrameLimiterFps(30))
        assertTrue(PerfPresets.validFrameLimiterFps(240))
        assertFalse(PerfPresets.validFrameLimiterFps(0))
        assertFalse(PerfPresets.validFrameLimiterFps(1000))
        assertFalse(PerfPresets.validFrameLimiterFps(-60))
    }

    @Test fun presetPinningAgreesWithCpuAllocationDefaults() {
        // Balanced/Performance game pinning must stay inside what the allocator
        // reserves for games on the 9300+ (mid cluster, optionally + prime).
        val allowed = setOf("4", "5", "6", "7")
        listOf(PerfPresets.Preset.BALANCED, PerfPresets.Preset.PERFORMANCE).forEach { preset ->
            val pinning = PerfPresets.for9300Plus(preset).cpuPinning.split(",").toSet()
            assertTrue("preset $preset pins outside the game cluster: $pinning", pinning.all { it in allowed })
        }
    }
}
