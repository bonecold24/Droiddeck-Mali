package com.droiddeck.launcher.gpu

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.core.DeviceSupport
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File

/**
 * The glibc PanVK driver bundled with the APK for the Linux guest: on first run
 * on a detected Mali GPU with no Linux Vulkan driver selected yet, imports the
 * bundled package (under assets/linux-drivers/) and selects it. The Tier-0
 * guest checks (vkcube, vulkaninfo) need this — the Android (bionic) driver
 * cannot be loaded by glibc processes.
 */
object GlibcDriverBundle {
    private const val TAG = "GlibcDriverBundle"
    private const val ASSET_DIR = "linux-drivers/panvk-kbase-glibc"
    private const val PREFS = "glibc_driver_bundle"
    private const val KEY_IMPORTED = "imported"

    fun ensureImportedAsync(context: Context) {
        if (!DeviceSupport.mali()) return
        val app = context.applicationContext
        Thread({
            try {
                ensureImported(app)
            } catch (e: Exception) {
                Log.w(TAG, "bundled glibc driver import failed: ${e.message}")
            }
        }, "glibc-driver-bundle").start()
    }

    private fun ensureImported(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IMPORTED, false)) return
        // Don't override an explicit user choice.
        if (SessionPrefs.linuxDriver(context).isNotEmpty()) {
            prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
            return
        }
        val assetFiles = try {
            context.assets.list(ASSET_DIR)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (assetFiles.isEmpty()) {
            Log.i(TAG, "no bundled glibc driver in APK assets; skipping")
            return
        }
        // Stage to a temp dir, then import.
        val stage = File(context.cacheDir, "glibc-driver-stage").apply {
            deleteRecursively(); mkdirs()
        }
        for (n in assetFiles) {
            context.assets.open("$ASSET_DIR/$n").use { inp ->
                File(stage, n).outputStream().use { inp.copyTo(it) }
            }
        }
        val mgr = LinuxVulkanDriverManager(context)
        val id = mgr.importDriverPackageDir(stage)
        stage.deleteRecursively()
        SessionPrefs.setLinuxDriver(context, id)
        prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
        Log.i(TAG, "bundled glibc driver imported as $id and selected")
    }
}
