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
}
