package com.droiddeck.launcher.core

/**
 * Battery / Balanced / Performance presets with concrete defaults for the
 * Dimensity 9300+ (1x X4 @ 3.4 GHz + 3x A720 @ 2.85 GHz + 4x A520 @ 2.0 GHz).
 *
 * - BATTERY: game on the little cluster only, 30 fps cap, default FEX preset.
 * - BALANCED: game on the mid cluster (matches CpuAllocationDefaults' game
 *   allocation), 60 fps cap, default FEX preset.
 * - PERFORMANCE: game on mid + prime, 120 fps cap, FEX PERFORMANCE preset.
 */
object PerfPresets {
    enum class Preset(val id: String, val label: String) {
        BATTERY("battery", "Battery"),
        BALANCED("balanced", "Balanced"),
        PERFORMANCE("performance", "Performance");

        companion object {
            fun byId(id: String?): Preset = values().firstOrNull { it.id == id } ?: BALANCED
        }
    }

    data class Mapping(
        val cpuPinning: String,
        val frameLimiterFps: Int?,
        val fexPresetId: String,
    )

    fun for9300Plus(preset: Preset): Mapping = when (preset) {
        Preset.BATTERY -> Mapping(cpuPinning = "0,1,2,3", frameLimiterFps = 30, fexPresetId = "")
        Preset.BALANCED -> Mapping(cpuPinning = "4,5,6", frameLimiterFps = 60, fexPresetId = "")
        Preset.PERFORMANCE -> Mapping(cpuPinning = "4,5,6,7", frameLimiterFps = 120, fexPresetId = "PERFORMANCE")
    }

    const val MAX_FRAME_LIMITER_FPS = 240

    fun validFrameLimiterFps(fps: Int?): Boolean = fps != null && fps in 1..MAX_FRAME_LIMITER_FPS

    /**
     * Environment fragment enforcing the frame limiter for the active
     * translation layer. A null fps disables limiting (empty map). For DXVK
     * the limiter is merged into any existing DXVK_CONFIG instead of
     * clobbering it.
     */
    fun frameLimiterEnv(
        fps: Int?,
        layer: ProtonSmartDefaults.TranslationLayer,
        existingDxvkConfig: String? = null,
    ): Map<String, String> {
        if (!validFrameLimiterFps(fps)) return emptyMap()
        return when (layer) {
            ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON ->
                mapOf("VKD3D_FRAME_RATE" to fps.toString())
            ProtonSmartDefaults.TranslationLayer.DXVK_SAREK,
            ProtonSmartDefaults.TranslationLayer.DXVK_STOCK -> {
                val line = "dxvk.maxFrameRate = $fps"
                val merged = if (existingDxvkConfig.isNullOrBlank()) line
                else existingDxvkConfig.trimEnd() + "\n" + line
                mapOf("DXVK_CONFIG" to merged)
            }
        }
    }
}
