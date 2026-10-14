package com.droiddeck.launcher.gpu

import org.json.JSONObject
import java.io.File

/**
 * Installs verified driver packages into a drivers root (on Android:
 * files/linux_vulkan_drivers). Pure JVM (no Android dependencies) so host unit
 * tests cover the versioning, coexistence and rollback behaviour.
 *
 * Each install lands in its own versioned directory:
 *   <name>-<packageVersion>-<binarySha256[0..8)>
 * e.g. panvk-kbase-test-0.1.0-rc3-universal-local1-0423314b
 *
 * Versions coexist side by side and an install never deletes another version:
 * rolling back is switching the selected id to [previousVersion]. Reinstalling
 * the identical binary is idempotent (returns the existing id).
 */
data class InstalledDriver(
    val id: String,
    val dir: File,
    val packageVersion: String,
    val abi: String,
    val warnings: List<String>,
)

data class InstalledVersion(val id: String, val packageVersion: String, val importedAt: Long)

object DriverPackageInstaller {

    /** The manager's ICD manifest name inside an installed driver directory. */
    const val ICD_NAME = "icd.json"

    /** The manager's metadata name inside an installed driver directory. */
    const val META_NAME = "meta.json"

    /**
     * <sanitized name>-<sanitized packageVersion>-<sha256 prefix>. Deterministic:
     * the same package always yields the same id.
     */
    @JvmStatic
    fun versionedId(pkg: DriverPackage): String {
        fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9.]+"), "-").trim('-', '.').take(48)
        val base = "${slug(pkg.name)}-${slug(pkg.packageVersion)}-${pkg.shaPrefix}"
            .replace(Regex("-{2,}"), "-")
        return base.ifEmpty { "driver-package-${pkg.shaPrefix}" }
    }

    /**
     * Verify [packageDir] and install it under [driversRoot]. Throws
     * [DriverPackageException] on any integrity failure; nothing is registered then.
     */
    @JvmStatic
    fun install(packageDir: File, driversRoot: File): InstalledDriver {
        val report = DriverPackage.verify(packageDir)
        val pkg = report.pkg
        val so = File(packageDir, pkg.libraryName)
        if (!ElfInspect.isAarch64Elf(so)) throw DriverPackageException(
            "\"${pkg.libraryName}\" is not a 64-bit AArch64 ELF shared library; not installed.")

        if (!driversRoot.isDirectory && !driversRoot.mkdirs()) throw DriverPackageException(
            "Cannot create the driver directory ${driversRoot.absolutePath}.")
        val id = uniqueId(driversRoot, versionedId(pkg), pkg)
        val dir = File(driversRoot, id)
        if (!dir.isDirectory && !dir.mkdirs()) throw DriverPackageException("Cannot create $dir.")

        for (name in pkg.files) {
            val src = File(packageDir, name)
            val dst = File(dir, name)
            src.copyTo(dst, overwrite = true)
        }

        // The session reads icd.json via VK_DRIVER_FILES: library_path must be absolute.
        val icd = JSONObject(File(packageDir, pkg.icdFileName).readText())
        icd.getJSONObject("ICD").put("library_path", File(dir, pkg.libraryName).absolutePath)
        File(dir, ICD_NAME).writeText(icd.toString(2))

        val meta = JSONObject()
        meta.put("schemaVersion", 1)
        meta.put("kind", "linux-vulkan-icd")
        meta.put("name", pkg.name)
        meta.put("driverVersion", pkg.packageVersion)
        meta.put("packageVersion", pkg.packageVersion)
        meta.put("abi", pkg.abi)
        meta.put("libc", if (pkg.isGlibc) "glibc" else "bionic")
        meta.put("backend", pkg.backend)
        meta.put("profile", pkg.profile)
        meta.put("binarySha256", pkg.binarySha256)
        meta.put("sourceLibraryName", pkg.libraryName)
        meta.put("libraryName", pkg.libraryName)
        meta.put("importedAt", System.currentTimeMillis())
        meta.put("versionedPackage", true)
        File(dir, META_NAME).writeText(meta.toString(2))

        return InstalledDriver(id, dir, pkg.packageVersion, pkg.abi, report.warnings)
    }

    /**
     * Installed versions of the driver named [driverName], newest first
     * (by [DriverVersion], then import time).
     */
    @JvmStatic
    fun listVersions(driversRoot: File, driverName: String): List<InstalledVersion> {
        return driversRoot.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            readMeta(dir)?.takeIf {
                it.optString("kind") == "linux-vulkan-icd" && it.optString("name") == driverName
            }?.let { InstalledVersion(dir.name, it.optString("driverVersion"), it.optLong("importedAt")) }
        }.sortedWith { a, b ->
            val v = DriverVersion.compare(b.packageVersion, a.packageVersion)
            if (v != 0) v else b.importedAt.compareTo(a.importedAt)
        }
    }

    /**
     * The installed version just older than [currentId] - the rollback target -
     * or null when there is nothing older.
     */
    @JvmStatic
    fun previousVersion(driversRoot: File, currentId: String): InstalledVersion? {
        val name = readMeta(File(driversRoot, currentId))?.optString("name") ?: return null
        val versions = listVersions(driversRoot, name)
        val i = versions.indexOfFirst { it.id == currentId }
        return if (i >= 0 && i + 1 < versions.size) versions[i + 1] else null
    }

    private fun uniqueId(root: File, base: String, pkg: DriverPackage): String {
        var id = base
        var n = 2
        while (true) {
            val dir = File(root, id)
            if (!dir.isDirectory) return id
            // Same binary already installed: reuse the id (idempotent reinstall).
            if (readMeta(dir)?.optString("binarySha256") == pkg.binarySha256) return id
            id = "$base-$n"
            n++
        }
    }

    private fun readMeta(dir: File): JSONObject? = try {
        val f = File(dir, META_NAME)
        if (f.isFile) JSONObject(f.readText()) else null
    } catch (e: Exception) {
        null
    }
}
