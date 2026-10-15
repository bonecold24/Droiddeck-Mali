package com.droiddeck.launcher.gpu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-runnable tests for the Directive #8 overlay damage repair logic.
 *
 * Preview-2 ran the Trixie overlay install on the Arch Linux ARM guest
 * (glibc 2.43) with no backup. The repair path restores the original files
 * from the linuxfs-r9 rootfs image.
 *
 * These tests cover the pure decision logic: whether a given /etc/os-release
 * content identifies a Bullseye guest (overlay allowed) or not (overlay
 * skipped/repaired).
 */
class OverlayDamageRepairTest {

    /**
     * Mirrors the os-release check in ensureRuntimeOverlayInstalled():
     * the overlay is only for Debian Bullseye.
     */
    private fun isBullseyeGuest(osReleaseText: String): Boolean =
        osReleaseText.contains("bullseye", ignoreCase = true)

    @Test
    fun bullseyeGuestAllowsOverlay() {
        val bullseye = """
            PRETTY_NAME="Debian GNU/Linux 11 (bullseye)"
            NAME="Debian GNU/Linux"
            ID=debian
            VERSION="11 (bullseye)"
        """.trimIndent()
        assertTrue(isBullseyeGuest(bullseye))
    }

    @Test
    fun archGuestSkipsOverlay() {
        // The real linuxfs-r9 rootfs.
        val arch = """
            NAME="Arch Linux ARM"
            PRETTY_NAME="Arch Linux ARM"
            ID=archarm
            ID_LIKE=arch
            BUILD_ID=rolling
        """.trimIndent()
        assertFalse(isBullseyeGuest(arch))
    }

    @Test
    fun trixieGuestSkipsOverlay() {
        val trixie = """
            PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
            ID=debian
            VERSION="13 (trixie)"
        """.trimIndent()
        assertFalse(isBullseyeGuest(trixie))
    }

    @Test
    fun emptyOsReleaseSkipsOverlay() {
        assertFalse(isBullseyeGuest(""))
    }

    @Test
    fun markerPathsAreWellDefined() {
        // The repair checks both the guest-side and legacy app-side markers.
        val guestMarker = ".trixie-overlay-installed"
        val legacyMarker = "linuxfs/.trixie-overlay-installed"
        assertTrue(guestMarker.isNotEmpty())
        assertTrue(legacyMarker.endsWith(guestMarker))
    }
}
