package com.droiddeck.launcher.comptest

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.core.Hashes
import org.json.JSONObject
import java.io.File

/**
 * Directive #10, task 1: the DIRECT Tier-0 driver path, independent of the
 * driver manager.
 *
 * Before the compute test (and every other guest stage), copy the glibc
 * libvulkan_panfrost.so and an ICD json with an absolute guest library_path
 * straight from the APK assets into <rootfs>/opt/comptest/, verifying the
 * .so SHA-256 against the bundled MANIFEST.json.
 *
 * The guest-side env (VK_DRIVER_FILES, VK_ICD_FILENAMES, COMPTEST_DRIVER_SO)
 * is injected for EVERY guest command in GuestCommand (after `/usr/bin/env -i`),
 * because extraEnv on the host ProcessBuilder never reaches the guest.
 */
object Tier0DriverDirect {
    private const val TAG = "Tier0DriverDirect"

    /** Guest-absolute paths. Must match GuestCommand's guest env injection. */
    const val GUEST_DIR = "/opt/comptest"
    const val GUEST_ICD = "/opt/comptest/icd.json"
    const val GUEST_SO = "/opt/comptest/libvulkan_panfrost.so"

    const val ASSET_DIR = "linux-drivers/panvk-kbase-glibc"
    const val ASSET_SO_NAME = "libvulkan_panfrost.so"
    const val ASSET_ICD_NAME = "panfrost_icd.aarch64.json"
    const val ASSET_MANIFEST_NAME = "MANIFEST.json"
    const val ASSET_META_NAME = "meta.json"

    data class InstallResult(
        val ok: Boolean,
        /** Step-by-step log lines (also written to the run's driver-install log). */
        val log: List<String>,
        val soSha256: String,
    )

    /**
     * Copy the bundled glibc driver + ICD into the guest-visible
     * <filesDir>/linuxfs/opt/comptest/ (== guest /opt/comptest/).
     * Returns a detailed log for the export zip's driver-install log.
     */
    fun install(context: Context): InstallResult {
        val log = ArrayList<String>()
        fun l(s: String) {
            log += s
            Log.i(TAG, s)
        }
        try {
            val app = context.applicationContext
            val am = app.assets
            val assetFiles = runCatching { am.list(ASSET_DIR)?.toList() ?: emptyList() }.getOrDefault(emptyList())
            l("asset dir $ASSET_DIR: found=${assetFiles.isNotEmpty()} files=$assetFiles")
            if (!assetFiles.contains(ASSET_SO_NAME)) {
                l("asset $ASSET_SO_NAME missing — direct driver install unavailable")
                return InstallResult(false, log, "")
            }
            val soSize = runCatching {
                am.open("$ASSET_DIR/$ASSET_SO_NAME").use { it.available().toLong() }
            }.getOrDefault(-1L)
            // Expected SHA from the bundled MANIFEST.json (CI generates it since
            // the v3-preview-4 fix; absent in v3-preview-3 and earlier).
            val expectedSha = runCatching {
                val text = am.open("$ASSET_DIR/$ASSET_MANIFEST_NAME").bufferedReader().readText()
                JSONObject(text).getString("binarySha256")
            }.getOrNull()
            l("MANIFEST.json binarySha256=${expectedSha ?: "<missing>"}")
            val abi = runCatching {
                val text = am.open("$ASSET_DIR/$ASSET_META_NAME").bufferedReader().readText()
                JSONObject(text).optString("abi", "")
            }.getOrDefault("")
            l("meta.json abi=${abi.ifEmpty { "<missing>" }}")

            val guestDir = File(app.filesDir, "linuxfs/opt/comptest").apply { mkdirs() }
            l("destination: ${guestDir.absolutePath} (guest $GUEST_DIR)")
            val dstSo = File(guestDir, ASSET_SO_NAME)
            am.open("$ASSET_DIR/$ASSET_SO_NAME").use { inp ->
                dstSo.outputStream().use { inp.copyTo(it) }
            }
            dstSo.setExecutable(true, false)
            val actualSha = Hashes.sha256(dstSo)
            l("copied $ASSET_SO_NAME: size=${dstSo.length()} (asset reported $soSize) sha256=$actualSha")
            if (expectedSha != null) {
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    l("SHA MISMATCH: MANIFEST.json says $expectedSha but copied file hashes to $actualSha — refusing to use")
                    return InstallResult(false, log, actualSha)
                }
                l("SHA ok: copied .so matches MANIFEST.json binarySha256")
            } else {
                l("SHA check skipped: no MANIFEST.json in assets (pre-fix APK)")
            }
            // Minimal ELF sanity for the log: magic + EM_AARCH64.
            l("ELF check: ${elfSummary(dstSo)}")

            // ICD with an absolute guest library_path.
            val icdText = am.open("$ASSET_DIR/$ASSET_ICD_NAME").bufferedReader().readText()
            val icd = JSONObject(icdText)
            icd.getJSONObject("ICD").put("library_path", GUEST_SO)
            File(guestDir, "icd.json").writeText(icd.toString(2))
            l("ICD written to $GUEST_ICD with library_path=$GUEST_SO")
            l("direct driver install: OK")
            return InstallResult(true, log, actualSha)
        } catch (t: Throwable) {
            log += "EXCEPTION ${t.javaClass.simpleName}: ${t.message}"
            Log.w(TAG, "direct driver install failed", t)
            return InstallResult(false, log, "")
        }
    }

    private fun elfSummary(so: File): String = runCatching {
        so.inputStream().use { inp ->
            val hdr = ByteArray(20)
            var read = 0
            while (read < 20) {
                val n = inp.read(hdr, read, 20 - read)
                if (n < 0) break
                read += n
            }
            if (read < 20) return "too small"
            val magic = hdr[0] == 0x7f.toByte() && hdr[1] == 'E'.code.toByte() &&
                hdr[2] == 'L'.code.toByte() && hdr[3] == 'F'.code.toByte()
            val machine = (hdr[18].toInt() and 0xff) or ((hdr[19].toInt() and 0xff) shl 8)
            "magic=$magic e_machine=$machine (183=AArch64)"
        }
    }.getOrDefault("unreadable")
}
