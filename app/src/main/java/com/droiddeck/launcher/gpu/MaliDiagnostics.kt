package com.droiddeck.launcher.gpu

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs
import org.json.JSONObject
import java.io.File

/**
 * The Mali driver diagnostics report: driver name/version, Vulkan API version,
 * extension count, kbase UAPI version, GPU id, active fallback-chain position and
 * the driver package's BUILD-PROVENANCE summary.
 *
 * [MaliDiagnosticsReport.rows] is pure (host-tested) and drives the UI;
 * [MaliDiagnostics.collect] gathers it on Android. Extension counts are not
 * measured on-device by this build (needs a Vulkan device query / PanProbe run,
 * NEEDS-REAL-MALI): the report says so and quotes the upstream REPORTED counts
 * as context, never as measured.
 */
data class MaliDiagnosticsReport(
    val driverName: String,
    val driverVersion: String,
    val abi: String,
    val vulkanApiVersion: String,
    val extensionCount: String,
    val kbaseUapi: String,
    val gpuId: String,
    val gpuName: String,
    val fallbackPosition: String,
    val fallbackReason: String,
    val provenanceSummary: String,
    val notes: List<String>,
) {
    /** Ordered label/value rows for the diagnostics UI. */
    fun rows(): List<Pair<String, String>> = listOf(
        "Driver" to "$driverName${driverVersion.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()}",
        "ABI" to abi.ifEmpty { "unknown" },
        "Vulkan API" to vulkanApiVersion.ifEmpty { "unknown" },
        "Extensions" to extensionCount,
        "GPU" to "$gpuName ($gpuId)",
        "kbase UAPI" to kbaseUapi,
        "Fallback chain" to fallbackPosition,
        "Chain reason" to fallbackReason,
        "Provenance" to provenanceSummary,
    ) + notes.map { "Note" to it }
}

object MaliDiagnostics {

    /** Upstream REPORTED extension counts (beta.3-era) - context only, not measured here. */
    const val REPORTED_INSTANCE_EXTENSIONS = 13
    const val REPORTED_DEVICE_EXTENSIONS = 181

    /** "0xC8700000", or "unknown" when the id was not readable. */
    @JvmStatic
    fun formatGpuId(gpuId: Long?): String =
        gpuId?.let { "0x" + it.toString(16).uppercase() } ?: "unknown"

    /** First [maxLines] non-empty lines of the provenance text. */
    @JvmStatic
    fun summarizeProvenance(text: String, maxLines: Int = 4): String {
        val head = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(maxLines)
        return if (head.isEmpty()) "not recorded" else head.joinToString("\n")
    }

    /**
     * Gathers the report on Android. Never throws: every source degrades to
     * "unknown"/"none" with a note.
     */
    @JvmStatic
    fun collect(context: Context): MaliDiagnosticsReport {
        return try {
            collectOrThrow(context)
        } catch (e: Exception) {
            MaliDiagnosticsReport(
                driverName = "unknown", driverVersion = "", abi = "",
                vulkanApiVersion = "", extensionCount = extensionsText(),
                kbaseUapi = "unknown", gpuId = "unknown", gpuName = "Mali (unknown model)",
                fallbackPosition = MaliFallbackPosition.DIAGNOSTIC_REPORT.name,
                fallbackReason = "Diagnostics collection failed: ${e.message}",
                provenanceSummary = "not recorded",
                notes = listOf("Diagnostics collection failed (${e.message}); showing what is known."),
            )
        }
    }

    private fun collectOrThrow(context: Context): MaliDiagnosticsReport {
        val manager = LinuxVulkanDriverManager(context)
        val profile = MaliDeviceProfile.detect()
        val installed = manager.enumerateInstalledDrivers()
        val panvkIds = installed.filter { manager.getBackend(it) == MaliDriverBundle.BACKEND }
        // The driver the session would draw with: newest glibc PanVK-kbase.
        val sessionPanvkId = panvkIds.filter { manager.isLinuxRuntimeUsable(it) }
            .maxWithOrNull(compareBy({ DriverVersion.compare(manager.getPackageVersion(it), "0") }, { it }))
        // The driver the report describes: the selected one, else the newest PanVK package.
        val displayId = SessionPrefs.linuxDriver(context).takeIf { it.isNotEmpty() && manager.isInstalled(it) }
            ?: panvkIds.maxWithOrNull(compareBy({ DriverVersion.compare(manager.getPackageVersion(it), "0") }, { it }))

        val decision = MaliFallbackChain.select(profile, sessionPanvkId, stockMaliPresent())

        val driverName: String
        val driverVersion: String
        val abi: String
        val apiVersion: String
        val provenance: String
        if (displayId != null) {
            val dir = manager.getDriverDir(displayId)
            driverName = manager.getDriverName(displayId)
            driverVersion = manager.getPackageVersion(displayId)
            abi = manager.getAbi(displayId)
            apiVersion = try {
                JSONObject(File(dir, "icd.json").readText()).getJSONObject("ICD").optString("api_version", "")
            } catch (e: Exception) {
                ""
            }
            provenance = File(dir, "BUILD-PROVENANCE.txt").takeIf { it.isFile }
                ?.readText().orEmpty()
        } else {
            driverName = "none imported"
            driverVersion = ""
            abi = ""
            apiVersion = ""
            provenance = ""
        }

        val positionLabel = when (decision.position) {
            MaliFallbackPosition.PANVK_KBASE -> "PanVK-kbase (preferred)"
            MaliFallbackPosition.STOCK_MALI_SPOOF -> "System Mali driver + spoof layer"
            MaliFallbackPosition.DIAGNOSTIC_REPORT -> "Diagnostic report (no usable driver)"
        }
        return MaliDiagnosticsReport(
            driverName = driverName,
            driverVersion = driverVersion,
            abi = abi,
            vulkanApiVersion = apiVersion,
            extensionCount = extensionsText(),
            kbaseUapi = profile.kbaseUapi ?: "unknown (not exposed in sysfs; needs a native probe)",
            gpuId = formatGpuId(profile.gpuId),
            gpuName = profile.gpuName,
            fallbackPosition = positionLabel,
            fallbackReason = decision.reason,
            provenanceSummary = summarizeProvenance(provenance),
            notes = profile.notes +
                if (decision.softwareGlAllowed) listOf("No GPU detected: software rendering is allowed (lab).")
                else emptyList(),
        )
    }

    private fun extensionsText(): String =
        "not measured on this device (needs a device run; upstream REPORTED " +
            "$REPORTED_INSTANCE_EXTENSIONS instance / $REPORTED_DEVICE_EXTENSIONS device)"

    private fun stockMaliPresent(): Boolean =
        File("/vendor/lib64/hw/vulkan.mali.so").exists() ||
            File("/vendor/lib64/egl/libGLES_mali.so").exists() ||
            File("/vendor/lib64/egl/mali/libGLES_mali.so").exists()
}
