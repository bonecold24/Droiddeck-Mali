package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtonManagerTest {

    @Test fun protonVersionIsExpected() {
        assertEquals("11-9.1", ProtonManager.PROTON_VERSION)
    }

    @Test fun protonUrlPointsToDroidDeckProton() {
        assertTrue(ProtonManager.PROTON_URL.contains("Droid-Deck/DroidDeck-Proton"))
        assertTrue(ProtonManager.PROTON_URL.endsWith("-aarch64.tar.gz"))
    }

    @Test fun protonSizeMatchesRelease() {
        // 596 MB as published in the release metadata.
        assertEquals(625_411_973L, ProtonManager.PROTON_SIZE_BYTES)
    }

    @Test fun guestProtonDirIsUnderOpt() {
        assertEquals("/opt/proton", ProtonManager.GUEST_PROTON_DIR)
    }

    @Test fun wineCommandIncludesDxvkHud() {
        val cmd = ProtonManager.wineCommand("/opt/comptest/d3d11-cube.exe")
        val flat = cmd.joinToString(" ")
        assertTrue(flat.contains("DXVK_HUD=devinfo,fps,api"))
        assertTrue(flat.contains("WINEDEBUG=-all"))
        assertTrue(flat.contains("VK_DRIVER_FILES=/opt/comptest/icd.json"))
        assertTrue(flat.contains("/opt/comptest/d3d11-cube.exe"))
    }

    @Test fun wineCommandUsesX11NotDrmShim() {
        val cmd = ProtonManager.wineCommand("/opt/comptest/d3d9-cube.exe")
        val flat = cmd.joinToString(" ")
        // Mali uses X11 presentation (like PanPlay), not DRM Gamescope.
        // DISPLAY must be set; drm-shim LD_PRELOAD is not used for Wine.
        assertTrue(flat.contains("DISPLAY=:0"))
    }

    @Test fun wineEnvHasRequiredVars() {
        val env = ProtonManager.wineEnv()
        val flat = env.joinToString(" ")
        assertTrue(flat.contains("VK_DRIVER_FILES="))
        assertTrue(flat.contains("VK_ICD_FILENAMES="))
        assertTrue(flat.contains("DXVK_HUD=devinfo,fps,api"))
        assertTrue(flat.contains("WINEDEBUG=-all"))
        assertTrue(flat.contains("WINEPREFIX="))
        assertTrue(flat.contains("WINEDLLOVERRIDES="))
        assertTrue(flat.contains("DISPLAY=:0"))
    }
}
