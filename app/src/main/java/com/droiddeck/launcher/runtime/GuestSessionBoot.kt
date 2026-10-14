package com.droiddeck.launcher.runtime

/**
 * Item 4: the Linux userland boot path, as pure unit-testable functions.
 *
 * These are the host-side twins of the boot-command construction in
 * SessionService.runSession, LinuxRuntime.prootPrefix /
 * LinuxRuntime.command and GuestCommand: the proot argv prefix, the
 * `/usr/bin/env -i` guest environment, the bind flags, the session-script
 * tail, the host environment and the guest shell. Nothing here executes the
 * guest; actually booting it is NEEDS-REAL-MALI.
 */
object GuestSessionBoot {
    /** The guest rootfs and every native guest binary are AArch64. */
    const val TARGET_ARCH = "aarch64"

    const val SESSION_SCRIPT = LinuxRuntime.SESSION_SCRIPT
    const val GUEST_RUNTIME_DIR = LinuxRuntime.GUEST_RUNTIME_DIR
    const val MODE_STEAM = LinuxRuntime.MODE_STEAM
    const val MODE_DESKTOP = LinuxRuntime.MODE_DESKTOP
    const val MODE_RUN = LinuxRuntime.MODE_RUN

    /**
     * The glibc dynamic loader inside the guest rootfs (Arch layout).
     * Mirrors what docs/development/steam-focus-debugging.md invokes directly:
     * `$R/usr/lib/ld-linux-aarch64.so.1 --library-path $R/usr/lib ...`.
     */
    fun glibcLoaderPath(rootPath: String): String = "$rootPath/usr/lib/ld-linux-aarch64.so.1"

    /**
     * The `--kernel-release=` value proot gets: the uname fields joined with
     * backslashes. Pure twin of LinuxRuntime.guestUtsname.
     */
    fun kernelRelease(sysname: String, hostname: String, release: String, version: String, machine: String): String =
        "\\$sysname\\$hostname\\$release\\$version\\$machine\\localdomain\\-1\\"

    /**
     * The proot argv prefix: binary, --kill-on-exit, the kernel release, the
     * identity (-i uid:uid, or -0 for fake root), the root and the cwd.
     * Pure twin of LinuxRuntime.prootPrefix.
     */
    fun prootPrefix(
        prootBinaryPath: String,
        rootPath: String,
        cwd: String,
        uid: Int,
        fakeRoot: Boolean,
        kernelRelease: String,
    ): List<String> = buildList {
        add(prootBinaryPath)
        add("--kill-on-exit")
        add("--kernel-release=$kernelRelease")
        if (fakeRoot) {
            add("-0")
        } else {
            add("-i")
            add("$uid:$uid")
        }
        add("-r")
        add(rootPath)
        add("-w")
        add(cwd)
    }

    /**
     * Every bind spec becomes a `-b <spec>` pair, in order.
     * Pure twin of the bind loop in LinuxRuntime.command.
     */
    fun bindArgs(specs: List<String>): List<String> = specs.flatMap { listOf("-b", it) }

    /**
     * The guest-side environment under `/usr/bin/env -i` for one-shot tool
     * commands. Pure twin of the env block in GuestCommand.runLogged.
     */
    fun toolGuestEnv(bwrapSocket: String): List<String> = listOf(
        "/usr/bin/env", "-i",
        "HOME=/root",
        "USER=root",
        "LANG=C.UTF-8",
        "PATH=/usr/local/bin:/usr/bin:/bin",
        "XDG_RUNTIME_DIR=$GUEST_RUNTIME_DIR",
        "XDG_DATA_HOME=/root/.local/share",
        "FLATPAK_BWRAP=${FlatpakManager.BWRAP}",
        "DROIDDECK_BWRAP_SOCKET=$bwrapSocket",
        "XDG_DATA_DIRS=/root/.local/share/flatpak/exports/share:/usr/local/share:/usr/share",
    )

    /**
     * The fixed guest environment of a session (the pref-independent part of
     * SessionService.addClientEnvironment): clean env, display plumbing and
     * the graphics stack pins.
     */
    fun baseClientEnv(): List<String> = listOf(
        "/usr/bin/env", "-i",
        "HOME=/root",
        "USER=root",
        "PATH=/usr/local/bin:/usr/bin:/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "XDG_RUNTIME_DIR=$GUEST_RUNTIME_DIR",
        "XDG_SESSION_TYPE=wayland",
        "WAYLAND_DISPLAY=wayland-0",
        "GAMESCOPE_FORCE_GENERAL_QUEUE=1",
        "MESA_LOADER_DRIVER_OVERRIDE=zink",
        "GALLIUM_DRIVER=zink",
        "LIBGL_KOPPER_DRI2=true",
        "MESA_DISK_CACHE_DATABASE=1",
        "GLIBC_TUNABLES=glibc.pthread.rseq=0:glibc.malloc.top_pad=16777216",
    )

    /**
     * The guest command tail: the session script, the mode, and the mode's
     * arguments. Mirrors SessionService.runSession: steam mode appends an
     * optional steam:// URL, run mode appends the program and its args.
     */
    fun sessionArgs(
        mode: String,
        steamUrl: String? = null,
        program: String? = null,
        programArgs: List<String> = emptyList(),
    ): List<String> = buildList {
        add(SESSION_SCRIPT)
        add(mode)
        when (mode) {
            MODE_STEAM -> if (steamUrl != null && steamUrl.startsWith("steam://")) add(steamUrl)
            MODE_RUN -> {
                require(!program.isNullOrEmpty()) { "run mode needs a program" }
                add(program)
                addAll(programArgs)
            }
        }
    }

    /**
     * The interactive shell the session terminal drops into
     * (SessionService.runSession's shellGuest).
     */
    fun terminalShellArgs(): List<String> =
        listOf("/bin/bash", "-c", "cd \"\$HOME\" && exec /bin/bash -i")

    /**
     * The host-side environment of the proot process: the loader, the tmp
     * dir, and the optional no-seccomp / fast-path / library-path switches.
     * Pure twin of the hostEnv block in SessionService.runSession.
     */
    fun hostEnv(
        prootLoaderPath: String,
        tmpDir: String,
        noSeccomp: Boolean,
        fastPathKey: String? = null,
        libraryPath: String? = null,
    ): Map<String, String> = buildMap {
        put("PROOT_LOADER", prootLoaderPath)
        put("PROOT_TMP_DIR", tmpDir)
        if (noSeccomp) put("PROOT_NO_SECCOMP", "1")
        if (fastPathKey != null) put("PROOT_FASTPATH", fastPathKey)
        if (!libraryPath.isNullOrEmpty()) put("LD_LIBRARY_PATH", libraryPath)
    }
}
