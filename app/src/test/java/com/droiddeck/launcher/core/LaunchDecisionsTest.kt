package com.droiddeck.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the launch/setup decision logic extracted from HomeActivity and
 * MainActivity. These mirror the exact branching of the activities so a
 * refactor cannot silently change what the user sees.
 */
class LaunchDecisionsTest {
    // HomeActivity.routeHome()

    @Test fun activeSteamSessionRoutesToSessionGuide() {
        assertEquals(
            LaunchDecisions.HomeDestination.SESSION_GUIDE,
            LaunchDecisions.homeDestination(activeSteamSession = true),
        )
    }

    @Test fun noActiveSessionRoutesToMain() {
        assertEquals(
            LaunchDecisions.HomeDestination.MAIN,
            LaunchDecisions.homeDestination(activeSteamSession = false),
        )
    }

    // MainActivity.onCreate() one-shot permission request

    private fun startup(
        storageGranted: Boolean = true,
        micEnabled: Boolean = false,
        micAsked: Boolean = false,
        micGranted: Boolean = true,
    ) = LaunchDecisions.startupPermissions(
        storagePermission = "android.permission.WRITE_EXTERNAL_STORAGE",
        micPermission = "android.permission.RECORD_AUDIO",
        storageGranted = storageGranted,
        micEnabled = micEnabled,
        micAsked = micAsked,
        micGranted = micGranted,
    )

    @Test fun storageRequestedWhenNotGranted() {
        val result = startup(storageGranted = false)
        assertEquals(listOf("android.permission.WRITE_EXTERNAL_STORAGE"), result.request)
        assertFalse(result.markMicAsked)
    }

    @Test fun nothingRequestedWhenEverythingGranted() {
        val result = startup()
        assertTrue(result.request.isEmpty())
        assertFalse(result.markMicAsked)
    }

    @Test fun micRequestedOnceWhenEnabledAndNeverAsked() {
        val result = startup(micEnabled = true, micAsked = false, micGranted = false)
        assertEquals(listOf("android.permission.RECORD_AUDIO"), result.request)
        assertTrue(result.markMicAsked)
    }

    @Test fun micNotRequestedAgainAfterBeingAsked() {
        val result = startup(micEnabled = true, micAsked = true, micGranted = false)
        assertTrue(result.request.isEmpty())
        assertFalse(result.markMicAsked)
    }

    @Test fun micNotRequestedWhenDisabled() {
        val result = startup(micEnabled = false, micAsked = false, micGranted = false)
        assertTrue(result.request.isEmpty())
        assertFalse(result.markMicAsked)
    }

    @Test fun micNotRequestedWhenAlreadyGranted() {
        val result = startup(micEnabled = true, micAsked = false, micGranted = true)
        assertTrue(result.request.isEmpty())
        assertFalse(result.markMicAsked)
    }

    @Test fun bothPermissionsRequestedTogether() {
        val result = startup(storageGranted = false, micEnabled = true, micAsked = false, micGranted = false)
        assertEquals(
            listOf("android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO"),
            result.request,
        )
        assertTrue(result.markMicAsked)
    }

    // MainActivity.refreshWifiDiscovery()

    @Test fun wifiDiscoveryNotBlockedWhenGranted() {
        assertFalse(LaunchDecisions.wifiDiscoveryBlocked(granted = true, asked = true, showRationaleForAny = false))
    }

    @Test fun wifiDiscoveryNotBlockedBeforeFirstAsk() {
        assertFalse(LaunchDecisions.wifiDiscoveryBlocked(granted = false, asked = false, showRationaleForAny = false))
    }

    @Test fun wifiDiscoveryNotBlockedWhenRationaleStillShowable() {
        assertFalse(LaunchDecisions.wifiDiscoveryBlocked(granted = false, asked = true, showRationaleForAny = true))
    }

    @Test fun wifiDiscoveryBlockedWhenDeniedAndRationaleGone() {
        assertTrue(LaunchDecisions.wifiDiscoveryBlocked(granted = false, asked = true, showRationaleForAny = false))
    }

    // MainActivity.startSession() gating

    private fun gate(
        busy: Boolean = false,
        steamSession: Boolean = true,
        phantomBlocksSteam: Boolean = false,
        runtimeInstalled: Boolean = true,
        adreno: Boolean = false,
        releaseAvailable: Boolean = false,
        mali: Boolean = true,
        icdAvailable: Boolean = true,
    ) = LaunchDecisions.sessionStartGate(
        busy = busy,
        steamSession = steamSession,
        phantomBlocksSteam = phantomBlocksSteam,
        runtimeInstalled = runtimeInstalled,
        adreno = adreno,
        releaseAvailable = releaseAvailable,
        mali = mali,
        icdAvailable = icdAvailable,
    )

    @Test fun busyBeatsEveryOtherGate() {
        assertEquals(
            LaunchDecisions.SessionStartGate.BUSY,
            gate(busy = true, phantomBlocksSteam = true, mali = true, icdAvailable = false),
        )
    }

    @Test fun steamSessionBlockedByPhantomLimit() {
        assertEquals(LaunchDecisions.SessionStartGate.PHANTOM_GATE, gate(phantomBlocksSteam = true))
    }

    @Test fun nonSteamSessionIgnoresPhantomLimit() {
        assertEquals(
            LaunchDecisions.SessionStartGate.START,
            gate(steamSession = false, phantomBlocksSteam = true),
        )
    }

    @Test fun missingRuntimeOnNonAdrenoWithReleaseWarns() {
        assertEquals(
            LaunchDecisions.SessionStartGate.NON_ADRENO_WARNING,
            gate(runtimeInstalled = false, adreno = false, releaseAvailable = true),
        )
    }

    @Test fun missingRuntimeOnNonAdrenoWithoutReleaseStillStarts() {
        // Matches the activity: the warning only fires when a release is known.
        assertEquals(
            LaunchDecisions.SessionStartGate.START,
            gate(runtimeInstalled = false, adreno = false, releaseAvailable = false),
        )
    }

    @Test fun missingRuntimeOnAdrenoStarts() {
        assertEquals(
            LaunchDecisions.SessionStartGate.START,
            gate(runtimeInstalled = false, adreno = true, releaseAvailable = true),
        )
    }

    @Test fun maliWithoutImportedDriverShowsDriverDialog() {
        assertEquals(
            LaunchDecisions.SessionStartGate.MALI_NO_DRIVER,
            gate(mali = true, icdAvailable = false),
        )
    }

    @Test fun maliWithImportedDriverStarts() {
        assertEquals(LaunchDecisions.SessionStartGate.START, gate(mali = true, icdAvailable = true))
    }

    @Test fun adrenoWithRuntimeStarts() {
        assertEquals(
            LaunchDecisions.SessionStartGate.START,
            gate(adreno = true, mali = false, runtimeInstalled = true),
        )
    }
}
