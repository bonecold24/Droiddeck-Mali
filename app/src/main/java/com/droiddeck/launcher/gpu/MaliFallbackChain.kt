package com.droiddeck.launcher.gpu

/**
 * The Mali driver fallback chain:
 *
 *   PanVK-kbase (preferred) -> stock Mali ICD + spoof layer -> diagnostic report
 *
 * [select] is pure (host unit-tested): given the detected device profile, the
 * installed PanVK driver and whether the system's Mali Vulkan driver exists, it
 * picks the chain position with a human-readable reason. It never crashes on an
 * untested kernel interface - a kbase UAPI that differs from the tested one falls
 * through with a clear message instead.
 *
 * Software rendering is only allowed when no real GPU was detected at all (lab):
 * on a real device a silent fallback to software GL is blocked - the user gets
 * the diagnostic report instead of a mysteriously slow session.
 */
enum class MaliFallbackPosition {
    /** PanVK on kbase: the imported driver, tested kernel interface. */
    PANVK_KBASE,

    /** The phone's own Mali Vulkan driver plus a spoof layer for the runtime. */
    STOCK_MALI_SPOOF,

    /** No usable driver: show the diagnostic report, do not start degraded. */
    DIAGNOSTIC_REPORT,
}

data class MaliFallbackDecision(
    val position: MaliFallbackPosition,
    /** Human-readable: why this position, shown in diagnostics and logs. */
    val reason: String,
    /** True only when no real GPU was detected at all (lab machines). */
    val softwareGlAllowed: Boolean,
    /** The driver id to draw with at [MaliFallbackPosition.PANVK_KBASE]; null otherwise. */
    val driverId: String?,
)

object MaliFallbackChain {

    /**
     * kbase UAPI versions the PanVK-kbase driver was tested against (REPORTED:
     * Samsung's 1.21 on the Tab S10 Ultra 38/38 run; the 14T Pro's is detected
     * at runtime, never assumed).
     */
    val TESTED_KBASE_UAPIS: Set<String> = setOf("1.21")

    /**
     * @param profile the detected device profile (null when detection itself failed)
     * @param panvkDriverId an installed PanVK-kbase driver id for the session, null when none
     * @param stockMaliPresent the system's Mali Vulkan driver exists on the phone
     */
    @JvmStatic
    fun select(
        profile: MaliDeviceProfile?,
        panvkDriverId: String?,
        stockMaliPresent: Boolean,
    ): MaliFallbackDecision {
        val noGpuEvidence = profile == null || (!profile.hasKbase && !profile.hasKnownGpu)
        // Software GL only where there is no real GPU at all - never a silent
        // fallback on a real device.
        val softwareGlAllowed = noGpuEvidence && !stockMaliPresent && panvkDriverId == null

        if (panvkDriverId != null && profile?.hasKbase == true) {
            val uapi = profile.kbaseUapi
            if (uapi == null || uapi in TESTED_KBASE_UAPIS) {
                val why = if (uapi == null)
                    "kbase node present but the UAPI version is not readable from sysfs - " +
                        "trying the imported PanVK-kbase driver; check the diagnostics screen " +
                        "if the session fails to draw."
                else
                    "kbase UAPI $uapi is a tested kernel interface for the imported PanVK-kbase driver."
                return MaliFallbackDecision(
                    MaliFallbackPosition.PANVK_KBASE,
                    "PanVK-kbase (preferred): $why",
                    softwareGlAllowed = false, driverId = panvkDriverId,
                )
            }
            val mismatch = "kbase UAPI $uapi differs from the tested ${TESTED_KBASE_UAPIS.sorted()}: " +
                "the imported PanVK-kbase driver is not used on this kernel interface."
            return if (stockMaliPresent) MaliFallbackDecision(
                MaliFallbackPosition.STOCK_MALI_SPOOF,
                "$mismatch Falling back to the system Mali driver + spoof layer.",
                softwareGlAllowed = false, driverId = null,
            ) else MaliFallbackDecision(
                MaliFallbackPosition.DIAGNOSTIC_REPORT,
                "$mismatch No system Mali driver found either.",
                softwareGlAllowed = false, driverId = null,
            )
        }

        if (stockMaliPresent) {
            val why = when {
                panvkDriverId == null -> "No PanVK-kbase driver imported."
                else -> "The kbase node is not accessible, so the imported PanVK-kbase driver cannot open the GPU."
            }
            return MaliFallbackDecision(
                MaliFallbackPosition.STOCK_MALI_SPOOF,
                "$why Using the system Mali driver + spoof layer.",
                softwareGlAllowed = false, driverId = null,
            )
        }

        return MaliFallbackDecision(
            MaliFallbackPosition.DIAGNOSTIC_REPORT,
            "No PanVK-kbase driver and no system Mali driver found. Import a driver " +
                "package first (the APK bundles one for the Mali-G720).",
            softwareGlAllowed = softwareGlAllowed, driverId = null,
        )
    }
}
