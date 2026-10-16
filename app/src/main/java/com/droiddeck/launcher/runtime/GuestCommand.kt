package com.droiddeck.launcher.runtime

import android.content.Context
import android.os.Environment
import com.droiddeck.launcher.core.HostProcess
import com.droiddeck.launcher.session.OrphanReaper
import com.droiddeck.launcher.session.SessionFiles
import com.droiddeck.launcher.session.SessionPaths
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File

object GuestCommand {
    fun run(context: Context, argv: List<String>, fakeRoot: Boolean = false, logName: String? = null,
            linkDir: File? = null, extraEnv: Map<String, String> = emptyMap(),
            onLine: (String) -> Unit): Int {
        val log = logName?.let {
            try {
                File(File(LinuxRuntime.logDir(context), SessionPaths.TOOLS_DIR).apply { mkdirs() }, "$it.log").printWriter()
            } catch (e: Exception) { null }
        }
        log?.println("== ${java.util.Date()} ${argv.joinToString(" ")}")
        try {
            // Directive #11 A6: filter proot stdio warnings from the onLine
            // callback (reports) but keep them in the raw log file.
            return runLogged(context, argv, fakeRoot, linkDir, extraEnv) { line ->
                log?.println(line); log?.flush()
                if (!isProotStdioWarning(line)) onLine(line)
            }
        } finally {
            log?.close()
        }
    }

    private fun runLogged(context: Context, argv: List<String>, fakeRoot: Boolean, linkDir: File?, extraEnv: Map<String, String>, onLine: (String) -> Unit): Int {
        val root = LinuxRuntime.rootDir(context)
        LinuxRuntime.writeAccounts(context)
        SessionFiles.stage(context, root)
        LinuxNetworkLinkComponent(context, root).publish()
        val runtimeDir = File(context.filesDir, ".flatpak-rt").apply { mkdirs() }
        val cmd = LinuxRuntime.prootPrefix(context, root, "/root", fakeRoot)
        if (linkDir != null) cmd.add(1, "--link2symlink")
        LinuxRuntime.binds(context, null, runtimeDir, Environment.getExternalStorageDirectory(), null)
            .forEach { cmd.add("-b"); cmd.add(it) }
        cmd += listOf(
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LANG=C.UTF-8",
            "PATH=/usr/local/bin:/usr/bin:/bin", "XDG_RUNTIME_DIR=${LinuxRuntime.GUEST_RUNTIME_DIR}",
            "XDG_DATA_HOME=/root/.local/share", "FLATPAK_BWRAP=${FlatpakManager.BWRAP}",
            "DROIDDECK_BWRAP_SOCKET=${BwrapSpawner.socket(context)}",
            "XDG_DATA_DIRS=/root/.local/share/flatpak/exports/share:/usr/local/share:/usr/share",
            // Directive #10 task 1: direct Tier-0 driver path. extraEnv on the
            // host ProcessBuilder NEVER reaches the guest (env -i wipes it), so
            // the Vulkan driver selection must be injected here, inside the
            // guest, for every guest command. /opt/comptest/icd.json and
            // /opt/comptest/libvulkan_panfrost.so are staged from the APK
            // assets by Tier0DriverDirect before the compute test.
            "VK_DRIVER_FILES=/opt/comptest/icd.json",
            "VK_ICD_FILENAMES=/opt/comptest/icd.json",
            "COMPTEST_DRIVER_SO=/opt/comptest/libvulkan_panfrost.so",
            // Directive #11 A6: disable the DroidDeck VR implicit layer during
            // comptest. It reports API 1.3 while the driver is 1.4, spamming
            // loader warnings. (Bumping the layer requires rebuilding its .so.)
            "VK_LOADER_LAYERS_DISABLE=VK_LAYER_DROIDDECK_vr",
            // Directive #11 A6: PanVK probes /dev/dri/renderD103 first and warns
            // "failed to query kernel driver version (VK_ERROR_INCOMPATIBLE_DRIVER)"
            // before falling back to the kbase path (/dev/mali0). This is
            // harmless — compute passes via the kbase fallback — and is
            // expected driver behavior. Do NOT change the driver binary.
        )
        cmd += argv
        val builder = ProcessBuilder(cmd).directory(root).redirectErrorStream(true)
        builder.environment().apply {
            put("PROOT_LOADER", LinuxRuntime.prootLoader(context).path)
            put("PROOT_TMP_DIR", context.cacheDir.path)
            if (linkDir != null) put("PROOT_L2S_DIR", linkDir.path)
            if (SessionPrefs.prootNoSeccomp(context)) put("PROOT_NO_SECCOMP", "1")
            LinuxRuntime.prootLibraryPath(context).takeIf { it.isNotEmpty() }?.let { put("LD_LIBRARY_PATH", it) }
            putAll(extraEnv)
        }
        val process = builder.start()
        val pid = HostProcess.pidOf(process)
        OrphanReaper.keep(pid)
        try {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            return process.waitFor()
        } finally {
            OrphanReaper.release(pid)
        }
    }

    /**
     * Directive #11 A6: the proot "can't sanitize binding" warnings for
     * /proc/self/fd stdio bindings. Pure — unit-tested.
     */
    fun isProotStdioWarning(line: String): Boolean {
        val l = line.lowercase()
        return l.contains("proot") && l.contains("sanitize") &&
            (l.contains("/proc/self/fd") || l.contains("binding"))
    }
}
