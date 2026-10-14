package com.droiddeck.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 4: the Linux userland boot path.
 *
 * These tests pin the pure, host-side construction of the guest boot command:
 * the proot argv prefix, the /usr/bin/env -i guest environment, the bind flags,
 * the session-script tail, the host environment and the expected guest shell.
 * Anything that actually *runs* the guest stays NEEDS-REAL-MALI.
 */
class GuestSessionBootTest {

    @Test fun prootPrefixStartsWithTheBinaryAndKillOnExit() {
        val argv = GuestSessionBoot.prootPrefix(
            prootBinaryPath = "/data/app/libproot.so",
            rootPath = "/data/data/x/files/linuxfs",
            cwd = "/root",
            uid = 10123,
            fakeRoot = false,
            kernelRelease = GuestSessionBoot.kernelRelease("Linux", "droiddeck", "6.1.0", "#1", "aarch64"),
        )
        assertEquals("/data/app/libproot.so", argv[0])
        assertTrue(argv.contains("--kill-on-exit"))
        assertEquals(listOf("-r", "/data/data/x/files/linuxfs", "-w", "/root"), argv.takeLast(4))
    }

    @Test fun prootPrefixUsesTheAppUidUnlessFakeRoot() {
        val real = GuestSessionBoot.prootPrefix("/p", "/r", "/root", 10123, false, "k")
        assertTrue(real.contains("-i"))
        assertTrue(real.contains("10123:10123"))
        assertFalse(real.contains("-0"))

        val fake = GuestSessionBoot.prootPrefix("/p", "/r", "/root", 10123, true, "k")
        assertTrue(fake.contains("-0"))
        assertFalse(fake.contains("-i"))
    }

    @Test fun kernelReleaseCarriesTheBackslashJoinedUnameFields() {
        assertEquals(
            "\\Linux\\droiddeck\\6.1.0\\#1\\aarch64\\localdomain\\-1\\",
            GuestSessionBoot.kernelRelease("Linux", "droiddeck", "6.1.0", "#1", "aarch64"),
        )
    }

    @Test fun bindSpecsBecomeDashBPairsInOrder() {
        assertEquals(
            listOf("-b", "/dev", "-b", "/proc", "-b", "/dev/urandom:/dev/random"),
            GuestSessionBoot.bindArgs(listOf("/dev", "/proc", "/dev/urandom:/dev/random")),
        )
        assertTrue(GuestSessionBoot.bindArgs(emptyList()).isEmpty())
    }

    @Test fun sessionArgsRunTheSessionScriptInSteamMode() {
        assertEquals(
            listOf("/usr/local/bin/droiddeck-session", "steam"),
            GuestSessionBoot.sessionArgs("steam"),
        )
    }

    @Test fun sessionArgsAppendASteamUrlOnlyWhenItIsOne() {
        assertEquals(
            listOf("/usr/local/bin/droiddeck-session", "steam", "steam://rungameid/123"),
            GuestSessionBoot.sessionArgs("steam", steamUrl = "steam://rungameid/123"),
        )
        assertEquals(
            listOf("/usr/local/bin/droiddeck-session", "steam"),
            GuestSessionBoot.sessionArgs("steam", steamUrl = "https://example.com/"),
        )
    }

    @Test fun sessionArgsRunModeCarriesTheProgramAndItsArgs() {
        assertEquals(
            listOf("/usr/local/bin/droiddeck-session", "run", "/root/game.sh", "--a", "b"),
            GuestSessionBoot.sessionArgs("run", program = "/root/game.sh", programArgs = listOf("--a", "b")),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun sessionArgsRunModeWithoutAProgramIsRejected() {
        GuestSessionBoot.sessionArgs("run")
    }

    @Test fun sessionArgsDesktopMode() {
        assertEquals(
            listOf("/usr/local/bin/droiddeck-session", "desktop"),
            GuestSessionBoot.sessionArgs("desktop"),
        )
    }

    @Test fun toolGuestEnvStartsWithEnvDashIAndTheFixedGuestHome() {
        val env = GuestSessionBoot.toolGuestEnv("/run/bwrap.sock")
        assertEquals("/usr/bin/env", env[0])
        assertEquals("-i", env[1])
        assertTrue(env.contains("HOME=/root"))
        assertTrue(env.contains("USER=root"))
        assertTrue(env.contains("LANG=C.UTF-8"))
        assertTrue(env.contains("PATH=/usr/local/bin:/usr/bin:/bin"))
        assertTrue(env.contains("XDG_RUNTIME_DIR=/run/droiddeck"))
        assertTrue(env.contains("XDG_DATA_HOME=/root/.local/share"))
        assertTrue(env.contains("FLATPAK_BWRAP=/usr/local/bin/droiddeck-bwrap"))
        assertTrue(env.contains("DROIDDECK_BWRAP_SOCKET=/run/bwrap.sock"))
        assertTrue(env.contains("XDG_DATA_DIRS=/root/.local/share/flatpak/exports/share:/usr/local/share:/usr/share"))
    }

    @Test fun baseClientEnvPinsTheDisplayAndGraphicsStack() {
        val env = GuestSessionBoot.baseClientEnv()
        assertEquals("/usr/bin/env", env[0])
        assertEquals("-i", env[1])
        assertTrue(env.contains("XDG_SESSION_TYPE=wayland"))
        assertTrue(env.contains("WAYLAND_DISPLAY=wayland-0"))
        assertTrue(env.contains("MESA_LOADER_DRIVER_OVERRIDE=zink"))
        assertTrue(env.contains("GALLIUM_DRIVER=zink"))
        assertTrue(env.contains("LIBGL_KOPPER_DRI2=true"))
        assertTrue(env.contains("GLIBC_TUNABLES=glibc.pthread.rseq=0:glibc.malloc.top_pad=16777216"))
        assertTrue(env.contains("MESA_DISK_CACHE_DATABASE=1"))
    }

    @Test fun hostEnvCarriesTheProotLoaderAndTmpDir() {
        val env = GuestSessionBoot.hostEnv("/data/app/libproot-loader.so", "/cache", noSeccomp = false)
        assertEquals("/data/app/libproot-loader.so", env["PROOT_LOADER"])
        assertEquals("/cache", env["PROOT_TMP_DIR"])
        assertFalse(env.containsKey("PROOT_NO_SECCOMP"))
        assertFalse(env.containsKey("PROOT_FASTPATH"))
        assertFalse(env.containsKey("LD_LIBRARY_PATH"))
    }

    @Test fun hostEnvAddsNoSeccompFastPathAndLibraryPathWhenAsked() {
        val env = GuestSessionBoot.hostEnv("/l", "/t", noSeccomp = true, fastPathKey = "abc123", libraryPath = "/libs")
        assertEquals("1", env["PROOT_NO_SECCOMP"])
        assertEquals("abc123", env["PROOT_FASTPATH"])
        assertEquals("/libs", env["LD_LIBRARY_PATH"])
    }

    @Test fun theTargetArchIsAarch64AndTheGlibcLoaderMatches() {
        assertEquals("aarch64", GuestSessionBoot.TARGET_ARCH)
        assertEquals(
            "/data/data/x/files/linuxfs/usr/lib/ld-linux-aarch64.so.1",
            GuestSessionBoot.glibcLoaderPath("/data/data/x/files/linuxfs"),
        )
    }

    @Test fun theSessionTerminalDropsIntoAnInteractiveBash() {
        assertEquals(
            listOf("/bin/bash", "-c", "cd \"\$HOME\" && exec /bin/bash -i"),
            GuestSessionBoot.terminalShellArgs(),
        )
    }
}
