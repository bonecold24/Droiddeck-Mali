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
     * Import the bundled driver from APK assets when needed and return its
     * driver id, or null when the APK carries no bundled driver. The recorded
     * id is reused across runs; a missing or non-glibc import is rejected.
     */
    fun ensureBundledImported(context: Context): String? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mgr = LinuxVulkanDriverManager(app)
        prefs.getString(KEY_BUNDLED_ID, null)?.let { id ->
            if (isValidSelection(mgr, id)) return id
            Log.i(TAG, "recorded bundled driver '$id' no longer valid; re-importing")
        }
        val assetFiles = try {
            app.assets.list(ASSET_DIR)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (assetFiles.isEmpty()) {
            Log.i(TAG, "no bundled glibc driver in APK assets; skipping")
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
            val id = mgr.importDriverPackageDir(stage)
            if (!isValidSelection(mgr, id)) {
                Log.w(TAG, "bundled driver '$id' failed glibc validation; not selecting it")
                return null
            }
            prefs.edit().putString(KEY_BUNDLED_ID, id).putBoolean(KEY_IMPORTED, true).apply()
            Log.i(TAG, "bundled glibc driver imported as $id")
            return id
        } catch (e: Exception) {
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
                // BACKUP: Save original loader and libraries before replacement.
                // This enables the "Repair guest runtime" button to restore the original state.
                val backupDir = File(app.filesDir, "linuxfs-backup").apply { mkdirs() }
                val backupMarker = File(backupDir, ".backup-complete")
                if (!backupMarker.exists()) {
                    Log.i(TAG, "Creating backup of original runtime...")
                    // Backup the loader symlink target
                    val origLd = File(rootfs, "lib/ld-linux-aarch64.so.1")
                    if (origLd.exists()) {
                        val backupLd = File(backupDir, "ld-linux-aarch64.so.1")
                        try {
                            if (java.nio.file.Files.isSymbolicLink(origLd.toPath())) {
                                val target = java.nio.file.Files.readSymbolicLink(origLd.toPath())
                                java.nio.file.Files.createSymbolicLink(backupLd.toPath(), target)
                            } else {
                                origLd.copyTo(backupLd, overwrite = true)
                            }
                            Log.i(TAG, "Backed up loader")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to backup loader: ${e.message}")
                        }
                    }
                    // Backup existing libraries that will be overwritten
                    val libBackupDir = File(backupDir, "libs").apply { mkdirs() }
                    for (dstDir in dstDirs) {
                        if (dstDir.exists()) {
                            dstDir.listFiles { f -> f.name.endsWith(".so") || f.name.contains(".so.") }?.forEach { orig ->
                                try {
                                    orig.copyTo(File(libBackupDir, orig.name), overwrite = true)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to backup ${orig.name}: ${e.message}")
                                }
                            }
                        }
                    }
                    backupMarker.createNewFile()
                    Log.i(TAG, "Backup complete")
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
     * Repairs the guest runtime by restoring the original loader and libraries
     * from the backup created during overlay installation.
     * Returns true if repair succeeded, false otherwise.
     */
    fun repairGuestRuntime(context: Context): Boolean {
        val app = context.applicationContext
        return try {
            val rootfs = File(app.filesDir, "linuxfs")
            val backupDir = File(app.filesDir, "linuxfs-backup")
            val backupMarker = File(backupDir, ".backup-complete")

            if (!backupMarker.exists()) {
                Log.w(TAG, "No backup found; cannot repair")
                return false
            }
            if (!rootfs.exists()) {
                Log.w(TAG, "Linux guest not installed; nothing to repair")
                return false
            }

            Log.i(TAG, "Repairing guest runtime from backup...")

            // Restore the loader
            val backupLd = File(backupDir, "ld-linux-aarch64.so.1")
            if (backupLd.exists()) {
                val dstLd = File(rootfs, "lib/ld-linux-aarch64.so.1")
                try {
                    if (java.nio.file.Files.isSymbolicLink(backupLd.toPath())) {
                        val target = java.nio.file.Files.readSymbolicLink(backupLd.toPath())
                        dstLd.delete()
                        java.nio.file.Files.createSymbolicLink(dstLd.toPath(), target)
                    } else {
                        backupLd.copyTo(dstLd, overwrite = true)
                    }
                    Log.i(TAG, "Restored loader")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore loader: ${e.message}")
                    return false
                }
            }

            // Restore libraries
            val libBackupDir = File(backupDir, "libs")
            if (libBackupDir.exists()) {
                val dstDirs = listOf(
                    File(rootfs, "lib/aarch64-linux-gnu"),
                    File(rootfs, "usr/lib/aarch64-linux-gnu")
                )
                libBackupDir.listFiles()?.forEach { backupLib ->
                    for (dstDir in dstDirs) {
                        val dst = File(dstDir, backupLib.name)
                        if (dst.exists()) {
                            try {
                                backupLib.copyTo(dst, overwrite = true)
                                dst.setExecutable(true, false)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to restore ${backupLib.name}: ${e.message}")
                            }
                        }
                    }
                }
                Log.i(TAG, "Restored libraries")
            }

            // Remove the overlay marker so it will be reinstalled on next run
            val overlayMarker = File(app.filesDir, "linuxfs/.trixie-overlay-installed")
            overlayMarker.delete()

            Log.i(TAG, "Guest runtime repair complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Repair failed: ${e.message}", e)
            false
        }
    }
}
