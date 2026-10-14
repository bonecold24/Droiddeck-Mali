package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.ProtonSmartDefaults
import org.junit.Assert.*
import org.junit.Test

class PerfOverlayTest {
    @Test fun disabledOverlayProducesNoEnv() {
        val state = PerfOverlay.OverlayState(enabled = false, showFps = true, showFrameTimes = true)
        assertTrue(PerfOverlay.env(state, ProtonSmartDefaults.TranslationLayer.DXVK_STOCK).isEmpty())
    }

    @Test fun fpsOnly() {
        val env = PerfOverlay.env(
            PerfOverlay.OverlayState(enabled = true, showFps = true, showFrameTimes = false),
            ProtonSmartDefaults.TranslationLayer.DXVK_SAREK,
        )
        assertEquals("fps", env["DXVK_HUD"])
    }

    @Test fun fpsPlusFrameTimes() {
        val env = PerfOverlay.env(
            PerfOverlay.OverlayState(enabled = true, showFps = true, showFrameTimes = true),
            ProtonSmartDefaults.TranslationLayer.DXVK_STOCK,
        )
        assertEquals("fps,frametimes", env["DXVK_HUD"])
    }

    @Test fun frameTimesOnly() {
        val env = PerfOverlay.env(
            PerfOverlay.OverlayState(enabled = true, showFps = false, showFrameTimes = true),
            ProtonSmartDefaults.TranslationLayer.DXVK_STOCK,
        )
        assertEquals("frametimes", env["DXVK_HUD"])
    }

    @Test fun mergesWithExistingHudWithoutDuplicates() {
        val env = PerfOverlay.env(
            PerfOverlay.OverlayState(enabled = true, showFps = true, showFrameTimes = false),
            ProtonSmartDefaults.TranslationLayer.DXVK_STOCK,
            existingHud = "compiler,fps",
        )
        assertEquals("compiler,fps", env["DXVK_HUD"])
    }

    @Test fun vkd3dProtonHasNoBuiltInHud() {
        // vkd3d-proton ships no HUD env var; saying otherwise would be a lie.
        val env = PerfOverlay.env(
            PerfOverlay.OverlayState(enabled = true, showFps = true, showFrameTimes = true),
            ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON,
        )
        assertTrue(env.isEmpty())
    }

    @Test fun stateRoundTripsThroughIds() {
        assertEquals(
            PerfOverlay.OverlayState(enabled = true, showFps = false, showFrameTimes = true),
            PerfOverlay.OverlayState.fromFlags(enabled = true, showFps = false, showFrameTimes = true),
        )
    }
}
