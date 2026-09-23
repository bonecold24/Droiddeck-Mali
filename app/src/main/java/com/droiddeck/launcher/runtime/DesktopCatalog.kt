package com.droiddeck.launcher.runtime

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.core.Downloader
import com.droiddeck.launcher.core.FileUtils
import org.json.JSONObject
import java.io.File

/**
 * The hosted packages that go into the runtime on request - the desktop, the emulators, and the
 * builds we mirror or make ourselves - described by desktop.json beside the runtime's own catalog.
 *
 * Two kinds. A `tar` extracts over the rootfs like the runtime itself (the same extractor, links
 * and modes preserved). An `appimage` is one file dropped in /opt/appimages with a .desktop entry
 * written for it; proot has no FUSE, so it runs extracted (APPIMAGE_EXTRACT_AND_RUN).
 */
object DesktopCatalog {
    private const val TAG = "DesktopCatalog"
    const val CATALOG_URL = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/desktop.json"

    class Entry(
        val id: String, val name: String, val tier: Int, val version: String, val kind: String,
        val url: String, val sha256: String, val size: Long, val notes: String,
        /** For an appimage: the icon name and menu category of its .desktop entry. */
        val icon: String, val category: String,
    )

    fun fetch(): List<Entry>? {
        val body = Downloader.downloadString(CATALOG_URL) ?: return null
        return try {
            val json = JSONObject(body)
            val arr = json.getJSONArray("packages")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    o.getString("id"), o.getString("name"), o.optInt("tier", 1), o.optString("version", ""),
                    o.optString("kind", "tar"), o.getString("url"), o.optString("sha256", ""),
                    o.optLong("size", 0L), o.optString("notes", ""),
                    o.optString("icon", "applications-games"), o.optString("category", "Game"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "catalog: $e"); null
        }
    }

    private fun marker(context: Context, id: String) = File(LinuxRuntime.rootDir(context), ".droiddeck-pkg-$id")

    /** The installed version of a package, or null. */
    fun installed(context: Context, id: String): String? =
        FileUtils.readString(marker(context, id))?.trim()?.takeIf { it.isNotEmpty() }

    // labwc comes with the hosted desktop package itself (its launcher is staged by the app at
    // every session, so it cannot tell whether the package is there); SessionFiles uses the same test.
    fun desktopInstalled(context: Context): Boolean =
        File(LinuxRuntime.rootDir(context), "usr/bin/labwc").isFile

    /** Downloads, verifies and installs one package. Returns null on success, else a message. */
    fun install(context: Context, entry: Entry, listener: LinuxRuntimeInstaller.ProgressListener?): String? {
        val root = LinuxRuntime.rootDir(context)
        if (!root.isDirectory) return "The Linux runtime is not installed"
        val download = File(context.cacheDir, "pkg-${entry.id}.download")
        try {
            listener?.onProgress("Downloading ${entry.name}", 0)
            val ok = Downloader.downloadFile(entry.url, download, true) { f ->
                listener?.onProgress("Downloading ${entry.name}", if (f < 0) -1 else Math.round(f * 100f))
            }
            if (!ok) return "Download failed"
            if (entry.sha256.isNotEmpty()) {
                listener?.onProgress("Verifying", -1)
                val actual = LinuxRuntimeInstaller.sha256(download)
                if (!entry.sha256.equals(actual, ignoreCase = true)) return "Checksum mismatch - nothing was changed"
            }
            listener?.onProgress("Installing ${entry.name}", -1)
            when (entry.kind) {
                "appimage" -> {
                    val dir = File(root, "opt/appimages").apply { mkdirs() }
                    val target = File(dir, "${entry.id}.AppImage")
                    // Some projects zip the AppImage (melonDS); the one file inside is what we want.
                    val placed = if (entry.url.endsWith(".zip", ignoreCase = true)) unzipAppImage(download, target)
                                 else download.renameTo(target)
                    if (!placed) return "Could not place the AppImage"
                    target.setExecutable(true, false)
                    FileUtils.writeString(File(root, "usr/share/applications/droiddeck-${entry.id}.desktop"),
                        "[Desktop Entry]\nType=Application\nName=${entry.name}\n" +
                        "Exec=env APPIMAGE_EXTRACT_AND_RUN=1 /opt/appimages/${entry.id}.AppImage\n" +
                        "Icon=${entry.icon}\nTerminal=false\nCategories=${entry.category};\n")
                }
                else -> if (!LinuxRuntimeInstaller.extract(download, root, listener)) return "Extraction failed"
            }
            FileUtils.writeString(marker(context, entry.id), entry.version)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "install ${entry.id}", e)
            return e.message ?: "Install failed"
        } finally {
            download.delete()
        }
    }

    private fun unzipAppImage(zip: File, target: File): Boolean {
        try {
            java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zip))).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".AppImage", ignoreCase = true)) {
                        java.io.FileOutputStream(target).use { out -> FileUtils.copy(zin, out) }
                        return true
                    }
                    entry = zin.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "unzip ${zip.name}", e)
        }
        return false
    }

    /**
     * Removes an appimage package outright. A tar package is spread through the rootfs and is
     * not tracked file by file; only its marker is dropped, which offers it again.
     */
    fun remove(context: Context, entry: Entry) {
        val root = LinuxRuntime.rootDir(context)
        if (entry.kind == "appimage") {
            File(root, "opt/appimages/${entry.id}.AppImage").delete()
            File(root, "usr/share/applications/droiddeck-${entry.id}.desktop").delete()
        }
        marker(context, entry.id).delete()
    }
}
