package com.droiddeck.launcher.session

/**
 * Item 5: the Steam client launch chain to the login screen, as pure
 * unit-testable functions.
 *
 * What the tree actually configures (verified against
 * tools/linuxfs/overlay/usr/local/bin/droiddeck-session and
 * droiddeck-steam-install):
 *
 * 1. SessionService boots the guest and runs
 *    `/usr/local/bin/droiddeck-session steam [steam://url...]`.
 * 2. droiddeck-session installs the client on first run
 *    (droiddeck-steam-install pulls steam_client_steamdeck_publicbeta_linuxarm64),
 *    sets STEAM_RUNTIME=1 and LD_LIBRARY_PATH to the client's steamrtarm64
 *    dirs, then execs the native ARM64 client `$steam_root/steamrtarm64/steam`
 *    in Deck mode (-gamepadui -steamdeck -steamos3, or -nobigpicture for the
 *    desktop UI) with -clientbeta steamdeck_publicbeta, -no-cef-sandbox,
 *    -cef-ozone-platform=x11 and the ANGLE-on-Vulkan CEF flags.
 * 3. There is NO x86-64 Steam client under FEX in this tree: the FEX fallback
 *    exists for games the client spawns (droiddeck-fex "steam" verbs
 *    run|waitforexitandrun wrap x86 ELF binaries; FEX presets come from
 *    FexPreset.kt). attempts() documents the chain as built.
 * 4. The chain terminates at the Steam login screen: no Steam account exists,
 *    no credentials are injected, loginusers.vdf is only *read* (to decide
 *    whether to re-ask for the ARM64 Proton), and no autologin flags exist.
 *
 * Actually starting the client is NEEDS-REAL-MALI.
 */
object SteamClientLaunch {
    /** The native ARM64 Steam client directory inside the Steam root. */
    const val CLIENT_DIR = "steamrtarm64"

    /** The client branch the session pins (-clientbeta). */
    const val BETA_CHANNEL = "steamdeck_publicbeta"

    /** The CDN manifest droiddeck-steam-install pulls. */
    const val CLIENT_MANIFEST = "steam_client_steamdeck_publicbeta_linuxarm64"

    enum class UiMode { GAMEPAD, DESKTOP }

    enum class ClientKind { NATIVE_ARM64, FEX_X86_64 }

    data class ClientAttempt(val kind: ClientKind, val binary: String, val available: Boolean)

    /**
     * The launch order for the Steam client: native ARM64 first. The x86-64
     * client under FEX is not shipped in this tree, so its attempt is marked
     * unavailable rather than fabricated.
     */
    fun attempts(steamRoot: String): List<ClientAttempt> = listOf(
        ClientAttempt(ClientKind.NATIVE_ARM64, "$steamRoot/$CLIENT_DIR/steam", available = true),
        ClientAttempt(ClientKind.FEX_X86_64, "$steamRoot/steamrt/steam", available = false),
    )

    fun firstAvailable(steamRoot: String): ClientAttempt? = attempts(steamRoot).firstOrNull { it.available }

    /** -gamepadui (+ Deck flags) or the plain desktop client. */
    fun uiArgs(mode: UiMode): List<String> = when (mode) {
        UiMode.GAMEPAD -> listOf("-gamepadui", "-steamdeck", "-steamos3")
        UiMode.DESKTOP -> listOf("-nobigpicture")
    }

    /** CEF rendering flags: ANGLE on Vulkan, except the desktop UI which disables the GPU. */
    fun cefArgs(desktop: Boolean): List<String> =
        if (desktop) listOf("-cef-disable-gpu")
        else listOf("-cef-force-gpu", "-cef-use-gl=angle", "-cef-use-angle=vulkan")

    /** Language, the pinned client beta, and the sandbox/platform flags. */
    fun baseArgs(language: String?): List<String> = buildList {
        language?.let { add("-language"); add(it) }
        add("-clientbeta"); add(BETA_CHANNEL)
        add("-no-cef-sandbox")
        add("-cef-ozone-platform=x11")
    }

    /** The environment droiddeck-session exports around the client. */
    fun clientEnv(steamRoot: String, existingLdPath: String? = null): Map<String, String> = mapOf(
        "STEAM_RUNTIME" to "1",
        "LD_LIBRARY_PATH" to "$steamRoot/$CLIENT_DIR:$steamRoot/$CLIENT_DIR/panorama" +
            (existingLdPath?.let { ":$it" } ?: ""),
        "SDL_VIDEO_X11_DGAMOUSE" to "0",
    )

    /**
     * Authentication flags the chain passes: none. The client always stops at
     * the login screen when no account is remembered; nothing here injects
     * credentials or enables autologin.
     */
    fun authArgs(): List<String> = emptyList()

    /**
     * Whether a game binary the client spawns needs the FEX fallback:
     * x86 (ELF machine 3) and x86-64 (62) go through `droiddeck-fex run`,
     * ARM (40) and ARM64 (183) run natively. Pure twin of the ELF-machine
     * check in droiddeck-session's run mode and droiddeck-fex's X86/ARM maps.
     */
    fun gameNeedsFex(elfMachine: Int?): Boolean = elfMachine == 3 || elfMachine == 62
}
