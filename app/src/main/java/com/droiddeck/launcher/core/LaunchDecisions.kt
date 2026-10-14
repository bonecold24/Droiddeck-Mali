package com.droiddeck.launcher.core

/**
 * Pure launch and setup decision logic, extracted from [com.droiddeck.launcher.HomeActivity]
 * and [com.droiddeck.launcher.MainActivity] so the branching can be unit-tested without
 * Android.
 *
 * The activities delegate to these functions and only translate the returned decisions
 * into intents, dialogs and permission requests. Behaviour must stay identical to the
 * inlined logic this replaces; [LaunchDecisionsTest] pins each branch.
 */
object LaunchDecisions {
    /** Where [com.droiddeck.launcher.HomeActivity] routes on launch. */
    enum class HomeDestination {
        /** An active Steam session exists: resume it via the home guide. */
        SESSION_GUIDE,
        /** No session: show the main launcher screen. */
        MAIN,
    }

    fun homeDestination(activeSteamSession: Boolean): HomeDestination =
        if (activeSteamSession) HomeDestination.SESSION_GUIDE else HomeDestination.MAIN

    /**
     * The one-shot permission request MainActivity performs in `onCreate`.
     *
     * @param storagePermission pass `Manifest.permission.WRITE_EXTERNAL_STORAGE`
     * @param micPermission pass `Manifest.permission.RECORD_AUDIO`
     * @param request permissions to ask for, in request order
     * @param markMicAsked whether the caller must persist "mic asked" (only when the
     *   mic permission is requested for the first time)
     */
    data class StartupPermissions(
        val request: List<String>,
        val markMicAsked: Boolean,
    )

    fun startupPermissions(
        storagePermission: String,
        micPermission: String,
        storageGranted: Boolean,
        micEnabled: Boolean,
        micAsked: Boolean,
        micGranted: Boolean,
    ): StartupPermissions {
        val wanted = ArrayList<String>(2)
        if (!storageGranted) wanted.add(storagePermission)
        var markAsked = false
        if (micEnabled && !micAsked && !micGranted) {
            wanted.add(micPermission)
            markAsked = true
        }
        return StartupPermissions(wanted, markAsked)
    }

    /**
     * Whether the Wi-Fi discovery permission row counts as permanently blocked:
     * denied, already asked once, and no permission still shows a rationale
     * (i.e. the user picked "don't ask again" or a policy blocks it).
     */
    fun wifiDiscoveryBlocked(granted: Boolean, asked: Boolean, showRationaleForAny: Boolean): Boolean =
        !granted && asked && !showRationaleForAny

    /** What MainActivity's `startSession` should do next. */
    enum class SessionStartGate {
        /** Another install/download is running; do nothing. */
        BUSY,
        /** A Steam session needs the phantom-process gate page first. */
        PHANTOM_GATE,
        /** No runtime installed on a non-Adreno device with a release available: warn first. */
        NON_ADRENO_WARNING,
        /** Mali device with no imported Linux Vulkan driver: open the driver dialog. */
        MALI_NO_DRIVER,
        /** All gates pass: start the session activity. */
        START,
    }

    fun sessionStartGate(
        busy: Boolean,
        steamSession: Boolean,
        phantomBlocksSteam: Boolean,
        runtimeInstalled: Boolean,
        adreno: Boolean,
        releaseAvailable: Boolean,
        mali: Boolean,
        icdAvailable: Boolean,
    ): SessionStartGate {
        if (busy) return SessionStartGate.BUSY
        if (steamSession && phantomBlocksSteam) return SessionStartGate.PHANTOM_GATE
        if (!runtimeInstalled && !adreno && releaseAvailable) return SessionStartGate.NON_ADRENO_WARNING
        if (runtimeInstalled && mali && !icdAvailable) return SessionStartGate.MALI_NO_DRIVER
        return SessionStartGate.START
    }
}
