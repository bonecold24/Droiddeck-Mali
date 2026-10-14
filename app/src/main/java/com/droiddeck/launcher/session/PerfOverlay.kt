package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.ProtonSmartDefaults

/**
 * In-game performance overlay: FPS counter and frame-time graph toggle.
 *
 * Backed by DXVK_HUD, so it only applies to the DXVK/DXVK-Sarek translation
 * layers. vkd3d-proton ships no built-in HUD; the overlay toggle is a no-op
 * there rather than a promise the driver cannot keep.
 */
object PerfOverlay {
    data class OverlayState(
        val enabled: Boolean = false,
        val showFps: Boolean = true,
        val showFrameTimes: Boolean = false,
    ) {
        companion object {
            fun fromFlags(enabled: Boolean, showFps: Boolean, showFrameTimes: Boolean) =
                OverlayState(enabled, showFps, showFrameTimes)
        }
    }

    fun env(
        state: OverlayState,
        layer: ProtonSmartDefaults.TranslationLayer,
        existingHud: String? = null,
    ): Map<String, String> {
        if (!state.enabled) return emptyMap()
        if (layer == ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON) return emptyMap()
        val parts = existingHud?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableList()
            ?: mutableListOf()
        if (state.showFps && "fps" !in parts) parts.add("fps")
        if (state.showFrameTimes && "frametimes" !in parts) parts.add("frametimes")
        if (parts.isEmpty()) return emptyMap()
        return mapOf("DXVK_HUD" to parts.joinToString(","))
    }
}
