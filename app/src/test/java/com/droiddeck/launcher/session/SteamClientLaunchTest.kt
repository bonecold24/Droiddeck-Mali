package com.droiddeck.launcher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 5: the Steam client launch chain to the login screen.
 *
 * Pins the launch order (native ARM64 Steam first), the UI/CEF/client-beta
 * argument sets, the client environment, and the hard stop at the login
 * screen: no account exists and no credentials are ever injected.
 * Actually starting the client is NEEDS-REAL-MALI.
 */
class SteamClientLaunchTest {

    private val root = "/data/data/x/files/linuxfs/root/.local/share/Steam"

    @Test fun nativeArm64SteamIsAttemptedFirst() {
        val attempts = SteamClientLaunch.attempts(root)
        assertEquals(SteamClientLaunch.ClientKind.NATIVE_ARM64, attempts.first().kind)
        assertEquals("$root/steamrtarm64/steam", attempts.first().binary)
        assertTrue(attempts.first().available)
    }

    @Test fun theX86ClientUnderFexIsDocumentedAsNotShipped() {
        val fallback = SteamClientLaunch.attempts(root)
            .first { it.kind == SteamClientLaunch.ClientKind.FEX_X86_64 }
        assertFalse(fallback.available)
        assertEquals(SteamClientLaunch.ClientKind.NATIVE_ARM64, SteamClientLaunch.firstAvailable(root)?.kind)
    }

    @Test fun gamepadUiRunsTheClientInDeckMode() {
        assertEquals(
            listOf("-gamepadui", "-steamdeck", "-steamos3"),
            SteamClientLaunch.uiArgs(SteamClientLaunch.UiMode.GAMEPAD),
        )
    }

    @Test fun desktopUiSkipsBigPicture() {
        assertEquals(listOf("-nobigpicture"), SteamClientLaunch.uiArgs(SteamClientLaunch.UiMode.DESKTOP))
    }

    @Test fun cefUsesAngleOnVulkanExceptOnTheDesktop() {
        assertEquals(
            listOf("-cef-force-gpu", "-cef-use-gl=angle", "-cef-use-angle=vulkan"),
            SteamClientLaunch.cefArgs(desktop = false),
        )
        assertEquals(listOf("-cef-disable-gpu"), SteamClientLaunch.cefArgs(desktop = true))
    }

    @Test fun baseArgsPinTheClientBetaChannelAndSandboxFlags() {
        val args = SteamClientLaunch.baseArgs(language = "english")
        assertTrue(args.contains("-clientbeta"))
        assertTrue(args.contains(SteamClientLaunch.BETA_CHANNEL))
        assertTrue(args.contains("-no-cef-sandbox"))
        assertTrue(args.contains("-cef-ozone-platform=x11"))
        assertEquals(listOf("-language", "english"), args.take(2))
    }

    @Test fun baseArgsSkipTheLanguageWhenNoneIsSet() {
        val args = SteamClientLaunch.baseArgs(language = null)
        assertFalse(args.contains("-language"))
        assertTrue(args.contains("-clientbeta"))
    }

    @Test fun clientEnvSetsTheSteamRuntimeAndItsLibraryPath() {
        val env = SteamClientLaunch.clientEnv(root)
        assertEquals("1", env["STEAM_RUNTIME"])
        assertEquals("$root/steamrtarm64:$root/steamrtarm64/panorama", env["LD_LIBRARY_PATH"])
        assertEquals("0", env["SDL_VIDEO_X11_DGAMOUSE"])
    }

    @Test fun clientEnvKeepsAnyExistingLibraryPathBehindSteamsOwn() {
        val env = SteamClientLaunch.clientEnv(root, existingLdPath = "/usr/lib")
        assertEquals("$root/steamrtarm64:$root/steamrtarm64/panorama:/usr/lib", env["LD_LIBRARY_PATH"])
    }

    @Test fun theChainTerminatesAtTheLoginScreenWithNoCredentials() {
        assertTrue(SteamClientLaunch.authArgs().isEmpty())
        val all = SteamClientLaunch.baseArgs("english") +
            SteamClientLaunch.uiArgs(SteamClientLaunch.UiMode.GAMEPAD) +
            SteamClientLaunch.cefArgs(desktop = false) +
            SteamClientLaunch.authArgs()
        assertTrue(all.none { it.contains("password", ignoreCase = true) || it.contains("autologin", ignoreCase = true) })
    }

    @Test fun x86GamesFallBackToFexWhileArm64GamesStayNative() {
        assertTrue(SteamClientLaunch.gameNeedsFex(3))    // i386
        assertTrue(SteamClientLaunch.gameNeedsFex(62))   // x86-64
        assertFalse(SteamClientLaunch.gameNeedsFex(183)) // arm64
        assertFalse(SteamClientLaunch.gameNeedsFex(40))  // arm
        assertFalse(SteamClientLaunch.gameNeedsFex(null))
    }
}
