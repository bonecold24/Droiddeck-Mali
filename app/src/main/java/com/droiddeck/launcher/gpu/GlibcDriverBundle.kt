package com.droiddeck.launcher.gpu

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.core.DeviceSupport
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File

/**
 * The glibc PanVK driver bundled with the APK for the Linux guest.
 *
 * Gate A2 behaviour: on app start (background thread) and before the first
 * Tier-0 guest check, the current Linux driver selection is validated. A
 * missing selection, a bionic (Android) selection — the v2 state — or any
 * selection that fails validation is automatically migrated to the bundled
 * glibc driver. A valid selection, including one the user chose by hand, is
 * always kept.
 *
 * The Tier-0 guest checks (vkcube, vulkaninfo) and Proton need this: the
 * Android (bionic) driver cannot be loaded by glibc processes.
 */
object GlibcDriverBundle {
    private const val TAG = "GlibcDriverBundle"
    private const val ASSET_DIR = "linux-drivers/panvk-kbase-glibc"
    private const val PREFS = "glibc_driver_bundle"
    private const val KEY_IMPORTED = "imported"
    private const val KEY_BUNDLED_ID = "bundled_id"

    /**
     * App-start entry point: import the bundled driver and migrate the
     * selection if needed. Runs off the UI thread.
     */
    fun ensureImportedAsync(context: Context) {
        if (!DeviceSupport.mali()) return
        val app = context.applicationContext
        Thread({
            try {
                ensureValidSelection(app)
            } catch (e: Exception) {
                Log.w(TAG, "bundled glibc driver setup failed: ${e.message}")
            }
        }, "glibc-driver-bundle").start()
    }

    /**
     * Blocking: ensure the bundled glibc driver is imported and the stored
     * Linux driver selection names a usable glibc driver, migrating it when
     * it does not. Safe to call before Tier-0 guest checks.
     */
    fun ensureValidSelection(context: Context) {
        if (!DeviceSupport.mali()) return
        val app = context.applicationContext
        val mgr = LinuxVulkanDriverManager(app)
        val current = SessionPrefs.linuxDriver(app)
        when (val d = LinuxDriverMigration.decide(
            currentSelection = current,
            isValid = { id -> isValidSelection(mgr, id) },
            bundledId = { ensureBundledImported(app) },
        )) {
            is LinuxDriverMigration.Decision.Keep ->
                Log.i(TAG, "keeping Linux driver selection '$current'")
            is LinuxDriverMigration.Decision.Migrate -> {
                SessionPrefs.setLinuxDriver(app, d.newId)
                Log.i(TAG, "migrated Linux driver selection '$current' -> '${d.newId}'")
            }
            is LinuxDriverMigration.Decision.Unavailable ->
                Log.w(TAG, "no valid Linux driver and no bundled driver available; leaving '$current'")
        }
        // Directive #11 A5: clean up unusable, non-selected driver dirs.
        // (Runs after migration so the newly selected driver is protected.)
        runCatching { cleanupUnusableDrivers(app) }
    }

    /**
     * True when [id] names an installed driver the Linux guest can actually
     * load: present on disk, glibc ABI, AArch64 ELF linking libc.so.6, and a
     * parseable ICD manifest. A bionic (Android) selection — the v2 state —
     * fails here and gets migrated.
     */
    fun isValidSelection(mgr: LinuxVulkanDriverManager, id: String): Boolean {
        if (id.isEmpty() || !mgr.isInstalled(id)) return false
        if (!mgr.isLinuxRuntimeUsable(id)) {
            Log.i(TAG, "selection '$id' is not a glibc driver (abi=${mgr.getAbi(id)}); migrating")
            return false
        }
        val result = LinuxDriverValidation.validateDriverDir(mgr.getDriverDir(id), mgr.getLibraryName(id))
        if (!result.valid) Log.i(TAG, "selection '$id' failed validation (${result.reason}); migrating")
        return result.valid
    }

    /**
     * Directive #11 A5: remove driver directories that are unusable AND not
     * selected. Logs one line per removal. Never removes the selected driver.
     * The bionic (Android) driver is unusable for the Linux guest; it is only
     * kept if it is the current selection (it never is after migration).
     * Returns the list of removed ids.
     */
    fun cleanupUnusableDrivers(context: Context): List<String> {
        val app = context.applicationContext
        val mgr = LinuxVulkanDriverManager(app)
        val selected = SessionPrefs.linuxDriver(app)
        val removed = ArrayList<String>()
        val driversRoot = java.io.File(app.filesDir, "linux_vulkan_drivers")
        val dirs = driversRoot.listFiles { f -> f.isDirectory } ?: return removed
        for (dir in dirs) {
            val id = dir.name
            if (id == selected) continue
            // Unusable: not a valid glibc driver for the guest.
            val usable = try {
                mgr.isInstalled(id) && mgr.isLinuxRuntimeUsable(id) &&
                    LinuxDriverValidation.validateDriverDir(
                        mgr.getDriverDir(id), mgr.getLibraryName(id)
                    ).valid
            } catch (e: Exception) { false }
            if (!usable) {
                val abi = runCatching { mgr.getAbi(id) }.getOrDefault("unknown")
                runCatching { dir.deleteRecursively() }.onSuccess {
                    val msg = "cleanup: removed unusable driver '$id' (abi=$abi, not selected)"
                    ilog(msg)
                    removed += id
                }.onFailure {
                    ilog("cleanup: failed to remove '$id': ${it.message}")
                }
            }
        }
        return removed
    }

    /**
     * Directive #10 task 2: step-by-step import log (asset found, path, size,
     * SHA ok, ABI read, destination, exception text). The executor snapshots it
     * into the run's logs as comptest-driver-import.log for the export zip.
     */
    private val importLogLines = java.util.Collections.synchronizedList(ArrayList<String>())
    private fun ilog(s: String) {
        importLogLines += s
        Log.i(TAG, s)
    }

    /** Drain the import log (called by the executor once per run). */
    fun drainImportLog(): List<String> = synchronized(importLogLines) {
        val out = importLogLines.toList()
        importLogLines.clear()
        out
    }

    /**
     * Import the bundled driver from APK assets when needed and return its
     * driver id, or null when the APK carries no bundled driver. The recorded
     * id is reused across runs; a missing or non-glibc import is rejected.
     */
    fun ensureBundledImported(context: Context): String? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mgr = LinuxVulkanDriverManager(app)
        prefs.getString(KEY_BUNDLED_ID, null)?.let { id ->
            if (isValidSelection(mgr, id)) {
                ilog("bundled driver '$id' already imported and valid; reusing")
                return id
            }
            ilog("recorded bundled driver '$id' no longer valid; re-importing")
        }
        val assetFiles = try {
            app.assets.list(ASSET_DIR)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        ilog("asset dir $ASSET_DIR: found=${assetFiles.isNotEmpty()} files=$assetFiles")
        if (assetFiles.isEmpty()) {
            ilog("no bundled glibc driver in APK assets; skipping")
            return null
        }
        // Stage to a temp dir, then import.
        val stage = File(app.cacheDir, "glibc-driver-stage").apply {
            deleteRecursively(); mkdirs()
        }
        try {
            for (n in assetFiles) {
                app.assets.open("$ASSET_DIR/$n").use { inp ->
                    File(stage, n).outputStream().use { inp.copyTo(it) }
                }
            }
            ilog("staged ${assetFiles.size} files to ${stage.absolutePath}")
            val id = mgr.importDriverPackageDir(stage)
            val destDir = mgr.getDriverDir(id)
            ilog("imported as '$id' -> $destDir")
            val abi = runCatching { mgr.getAbi(id) }.getOrDefault("?")
            ilog("ABI read: $abi")
            if (!isValidSelection(mgr, id)) {
                ilog("bundled driver '$id' failed glibc validation; not selecting it")
                return null
            }
            prefs.edit().putString(KEY_BUNDLED_ID, id).putBoolean(KEY_IMPORTED, true).apply()
            ilog("bundled glibc driver imported as $id (destination $destDir)")
            return id
        } catch (e: Exception) {
            ilog("EXCEPTION ${e.javaClass.simpleName}: ${e.message}")
            Log.w(TAG, "bundled glibc driver import failed: ${e.message}")
            return null
        } finally {
            stage.deleteRecursively()
        }
    }

    /**
     * Install the Trixie runtime overlay into the Linux guest rootfs.
     * Must be called before Stage 0 / B2 / vkcube run.
     * The overlay provides libc6 2.41, libwayland-client 1.24.0, and
     * SPIRV-Tools — all required for dlopen() of libvulkan_panfrost.so.
     * Verified via QEMU: dlopen(RTLD_NOW) + dlsym(vk_icdGetInstanceProcAddr) PASS.
     */
    fun ensureRuntimeOverlayInstalled(context: Context): Boolean {
        // Directive #8: DISABLED. The Trixie overlay was built for a Debian
        // Bullseye guest, but the real guest is Arch Linux ARM (glibc 2.43).
        // Installing Debian Trixie libraries (glibc 2.41) would downgrade the
        // guest. The driver already loads on the stock guest (QEMU VERIFIED).
        // Use checkAndRepairOverlayDamage() instead to undo preview-2 damage.
        Log.i(TAG, "Trixie overlay install is disabled (guest is Arch, not Bullseye)")
        return true
    }

    @Deprecated("Disabled per Directive #8; use checkAndRepairOverlayDamage()")
    private fun ensureRuntimeOverlayInstalledImpl(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val overlayMarker = File(app.filesDir, "linuxfs/.trixie-overlay-installed")
        if (overlayMarker.exists()) {
            Log.i(TAG, "Trixie overlay already installed")
            return true
        }
        return try {
            val rootfs = File(app.filesDir, "linuxfs")
            if (!rootfs.exists()) {
                Log.w(TAG, "Linux guest not installed; skipping overlay")
                return false
            }
            // Directive #7 Task 3: the overlay is Debian Trixie libraries for a
            // Bullseye guest. The actual guest is Arch Linux ARM (glibc 2.43),
            // which already satisfies the driver's GLIBC_2.38+ requirement.
            // Installing the overlay would DOWNGRADE glibc 2.43 -> 2.41.
            // Skip the overlay unless the guest is Debian Bullseye.
            val osRelease = File(rootfs, "etc/os-release")
            val isBullseye = runCatching {
                osRelease.readText().contains("bullseye", ignoreCase = true)
            }.getOrDefault(false)
            if (!isBullseye) {
                val id = runCatching {
                    osRelease.readText().lineSequence()
                        .firstOrNull { it.startsWith("ID=") }?.substringAfter("=")
                        ?.trim('"')
                }.getOrNull() ?: "unknown"
                Log.i(TAG, "Guest is '$id', not Debian Bullseye; skipping Trixie overlay " +
                    "(guest glibc already satisfies driver requirements)")
                return true
            }
            // The APK contains trixie-runtime-overlay.tar (decompressed by the build).
            // CI verifies the .tar.gz SHA before bundling; the asset is the .tar.
            app.assets.open("runtime-overlay/trixie-runtime-overlay.tar").use { inp ->
                val tmpTar = File(app.cacheDir, "trixie-overlay.tar")
                tmpTar.outputStream().use { inp.copyTo(it) }
                val expectedSha = app.assets.open("runtime-overlay/trixie-runtime-overlay.sha256")
                    .bufferedReader().readText().trim()
                val actualSha = sha256Of(tmpTar)
                if (actualSha != expectedSha) {
                    Log.e(TAG, "Overlay SHA256 mismatch")
                    tmpTar.delete()
                    return false
                }
                // Extract to temp dir first, then handle UsrMerge vs non-UsrMerge layout.
                // Bullseye uses non-UsrMerge (/lib is a real dir); Trixie uses UsrMerge.
                val tmpExtract = File(app.cacheDir, "trixie-overlay-extract").apply {
                    deleteRecursively(); mkdirs()
                }
                val proc = ProcessBuilder("tar", "-xf", tmpTar.absolutePath, "-C", tmpExtract.absolutePath)
                    .redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                val rc = proc.waitFor()
                tmpTar.delete()
                if (rc != 0) {
                    Log.e(TAG, "Overlay extraction failed (rc=$rc): $out")
                    tmpExtract.deleteRecursively()
                    return false
                }
                // Detect layout: if rootfs/lib is a symlink, it's UsrMerge.
                val libDir = File(rootfs, "lib")
                val isUsrMerge = try {
                    java.nio.file.Files.isSymbolicLink(libDir.toPath())
                } catch (e: Exception) { false }
                // Copy .so files to the correct locations.
                val srcLib = File(tmpExtract, "usr/lib/aarch64-linux-gnu")
                val dstDirs = if (isUsrMerge) {
                    listOf(File(rootfs, "usr/lib/aarch64-linux-gnu"))
                } else {
                    // Non-UsrMerge: ld.so searches /lib/aarch64-linux-gnu BEFORE /usr/lib.
                    // Install to /lib to ensure our versions win.
                    listOf(File(rootfs, "lib/aarch64-linux-gnu"), File(rootfs, "usr/lib/aarch64-linux-gnu"))
                }
                for (dstDir in dstDirs) {
                    dstDir.mkdirs()
                    srcLib.listFiles { f -> f.name.endsWith(".so") || f.name.contains(".so.") }?.forEach { src ->
                        val dst = File(dstDir, src.name)
                        // Handle symlinks: copy the link target, not the link itself.
                        if (java.nio.file.Files.isSymbolicLink(src.toPath())) {
                            val target = java.nio.file.Files.readSymbolicLink(src.toPath())
                            val targetFile = File(src.parentFile, target.toString())
                            if (targetFile.exists()) {
                                targetFile.copyTo(dst, overwrite = true)
                                // proot execve() needs +x; tar/copy may leave 0644.
                                dst.setExecutable(true, false)
                            }
                        } else {
                            src.copyTo(dst, overwrite = true)
                            dst.setExecutable(true, false)
                        }
                    }
                }
                // CRITICAL: Update the dynamic loader symlink atomically with libc.
                // In Bullseye, /lib/ld-linux-aarch64.so.1 -> /lib/aarch64-linux-gnu/ld-2.31.so.
                // If we install libc6 2.41 but leave ld-2.31.so, EVERY binary crashes
                // with "undefined symbol: ... version GLIBC_PRIVATE".
                // We must point /lib/ld-linux-aarch64.so.1 to the new 2.41 loader.
                val srcLd = File(tmpExtract, "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
                if (srcLd.exists() && !isUsrMerge) {
                    val dstLd = File(rootfs, "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
                    srcLd.copyTo(dstLd, overwrite = true)
                    dstLd.setExecutable(true, false)
                    // Atomically update the /lib/ld-linux-aarch64.so.1 symlink.
                    val interpLink = File(rootfs, "lib/ld-linux-aarch64.so.1")
                    val tmpLink = File(rootfs, "lib/.ld-linux-aarch64.so.1.tmp")
                    try {
                        // Create new symlink, then atomically rename over the old one.
                        java.nio.file.Files.createSymbolicLink(
                            tmpLink.toPath(),
                            java.nio.file.Path.of("aarch64-linux-gnu/ld-linux-aarch64.so.1")
                        )
                        java.nio.file.Files.move(
                            tmpLink.toPath(), interpLink.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                        Log.i(TAG, "Updated /lib/ld-linux-aarch64.so.1 -> aarch64-linux-gnu/ld-linux-aarch64.so.1 (2.41)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update loader symlink: ${e.message}")
                        tmpLink.delete()
                        return false
                    }
                }
                tmpExtract.deleteRecursively()
            }
            overlayMarker.createNewFile()
            Log.i(TAG, "Trixie runtime overlay installed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Overlay install failed: ${e.message}", e)
            false
        }
    }

    private fun sha256Of(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(8192)
            var n = inp.read(buf)
            while (n > 0) { md.update(buf, 0, n); n = inp.read(buf) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Directive #8.1: check for Trixie overlay damage on a non-Bullseye guest
     * and repair it on startup.
     *
     * Preview-2 ran ensureRuntimeOverlayInstalled() on the Arch Linux ARM guest
     * (glibc 2.43) with no backup. The Debian Trixie overlay (glibc 2.41)
     * would downgrade critical libraries.
     *
     * Detection: the overlay marker file exists at linuxfs/.trixie-overlay-installed.
     * (The Stage 0 guest probe also reports pacman -Qkk glibc for confirmation.)
     *
     * Repair: for every path in the overlay manifest (assets/runtime-overlay/
     * trixie-runtime-overlay.tar), restore the original file from the
     * linuxfs-r9 rootfs image. The image is downloaded only if not cached.
     * The marker is deleted after a successful repair. Returns the list of
     * restored relative paths (empty if nothing to repair).
     */
    fun checkAndRepairOverlayDamage(context: Context): List<String> {
        val app = context.applicationContext
        val restored = ArrayList<String>()
        try {
            val rootfs = File(app.filesDir, "linuxfs")
            val marker = File(rootfs, ".trixie-overlay-installed")
            if (!marker.exists()) {
                // Also check the legacy app-side marker location.
                val legacyMarker = File(app.filesDir, "linuxfs/.trixie-overlay-installed")
                if (!legacyMarker.exists()) return restored
            }
            Log.w(TAG, "Trixie overlay marker found on non-Bullseye guest; repairing...")

            // List the overlay manifest paths from the APK asset.
            // Directive #8: only the manifest (347 paths) is bundled, not the 43MB tar.
            val manifestPaths = ArrayList<String>()
            app.assets.open("runtime-overlay/trixie-overlay-manifest.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val path = line.trim()
                    if (path.isNotEmpty() && !path.endsWith("/")) manifestPaths.add(path)
                }
            }
            Log.i(TAG, "Overlay manifest has ${manifestPaths.size} file paths to restore")

            // Obtain the original rootfs image (cached or downloaded).
            val rootfsImage = getOrDownloadRootfsImage(app)
                ?: run {
                    Log.e(TAG, "Cannot repair overlay damage: rootfs image unavailable")
                    return restored
                }

            // Extract only the manifest paths from the image into a temp dir,
            // then copy them over the damaged files.
            val tmpRestore = File(app.cacheDir, "overlay-repair").apply {
                deleteRecursively(); mkdirs()
            }
            try {
                // Use tar with explicit file list (zstd-compressed tar).
                val pb = ProcessBuilder(
                    listOf("sh", "-c",
                        "zstd -d -c '${rootfsImage.absolutePath}' | tar -x -C '${tmpRestore.absolutePath}' " +
                            manifestPaths.joinToString(" ") { "'$it'" })
                ).redirectErrorStream(true)
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText()
                val rc = proc.waitFor()
                if (rc != 0) {
                    Log.e(TAG, "Rootfs selective extract failed (rc=$rc): ${out.take(500)}")
                    return restored
                }
                for (relPath in manifestPaths) {
                    val src = File(tmpRestore, relPath)
                    if (!src.exists()) {
                        Log.w(TAG, "Original not in rootfs image, skipping: $relPath")
                        continue
                    }
                    val dst = File(rootfs, relPath)
                    try {
                        dst.parentFile?.mkdirs()
                        // Preserve symlinks.
                        if (java.nio.file.Files.isSymbolicLink(src.toPath())) {
                            val target = java.nio.file.Files.readSymbolicLink(src.toPath())
                            dst.delete()
                            java.nio.file.Files.createSymbolicLink(dst.toPath(), target)
                        } else {
                            src.copyTo(dst, overwrite = true)
                            // Restore executable bit for loader/libs.
                            if (relPath.contains("ld-linux") || relPath.endsWith(".so") || relPath.contains(".so.")) {
                                dst.setExecutable(true, false)
                            }
                        }
                        restored.add(relPath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore $relPath: ${e.message}")
                    }
                }
            } finally {
                tmpRestore.deleteRecursively()
            }

            // Delete the marker so we don't repair again.
            marker.delete()
            File(app.filesDir, "linuxfs/.trixie-overlay-installed").delete()
            Log.i(TAG, "Overlay damage repair complete: ${restored.size} files restored")
        } catch (t: Throwable) {
            Log.e(TAG, "Overlay damage repair failed: ${t.message}", t)
        }
        return restored
    }

    /**
     * Returns the cached linuxfs-r9 rootfs image, downloading it if necessary.
     * Returns null if the download fails.
     */
    private fun getOrDownloadRootfsImage(app: Context): File? {
        val cached = File(app.filesDir, "linuxfs-r9.tar.zst")
        if (cached.isFile && cached.length() > 100_000_000) {
            Log.i(TAG, "Using cached rootfs image (${cached.length()} bytes)")
            return cached
        }
        return try {
            Log.i(TAG, "Downloading linuxfs-r9 rootfs image for overlay repair...")
            val url = java.net.URL(
                "https://github.com/Droid-Deck/DroidDeck-Components/releases/download/linuxfs-r9/linuxfs.tar.zst")
            url.openStream().use { inp ->
                cached.outputStream().use { out -> inp.copyTo(out) }
            }
            if (cached.length() > 100_000_000) cached else null
        } catch (t: Throwable) {
            Log.e(TAG, "Rootfs image download failed: ${t.message}")
            cached.delete()
            null
        }
    }
}
