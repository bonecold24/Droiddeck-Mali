package com.droiddeck.launcher.core

/**
 * Per-game / global compatibility-layer selection honoring the Mali GPU verdicts
 * (docs/GPU.md §5):
 *
 * - D3D9  → DXVK-Sarek lineage (device proof on G720).
 * - D3D11 → stock DXVK at FL 11_1 (the main catalog tier).
 * - D3D12 → VKD3D-Proton, STRETCH ONLY: feature level is capped at 11_1 because
 *   sparse resources are an architectural NO-GO on the kbase driver (§4), so
 *   FL 12_0+ paths must never be offered and the UI must warn the user.
 *
 * All REPORTED capabilities; nothing here is OBSERVED on real Mali hardware.
 */
object ProtonSmartDefaults {
    enum class D3dTier { D3D9, D3D11, D3D12, UNKNOWN }

    enum class TranslationLayer(val id: String, val label: String) {
        DXVK_SAREK("dxvk-sarek", "DXVK-Sarek"),
        DXVK_STOCK("dxvk", "DXVK"),
        VKD3D_PROTON("vkd3d-proton", "VKD3D-Proton");

        companion object {
            fun byId(id: String?): TranslationLayer? = values().firstOrNull { it.id == id }
        }
    }

    data class TierHints(val d3d9: Boolean = false, val d3d11: Boolean = false, val d3d12: Boolean = false)

    data class D3d12Warning(
        val featureLevelCap: String,
        val shaderModelCap: String,
        val message: String,
    )

    data class LayerSelection(val layer: TranslationLayer, val d3d12Warning: D3d12Warning?)

    /** Mali verdict ceiling: FL 11_1 (sparse is a kbase NO-GO), SM 6.0 pre-denorm-fix. */
    const val MALI_FEATURE_LEVEL_CAP = "11_1"
    const val MALI_SHADER_MODEL_CAP = "6_0"

    private val d3d12Warning = D3d12Warning(
        featureLevelCap = MALI_FEATURE_LEVEL_CAP,
        shaderModelCap = MALI_SHADER_MODEL_CAP,
        message = "D3D12 is stretch-only on this GPU: the feature level is capped at 11_1 " +
            "(sparse resources are unsupported by the kbase driver), so many AAA D3D12 " +
            "titles that require feature level 12_0 will not start.",
    )

    fun select(tier: D3dTier): LayerSelection = when (tier) {
        D3dTier.D3D9 -> LayerSelection(TranslationLayer.DXVK_SAREK, null)
        D3dTier.D3D11 -> LayerSelection(TranslationLayer.DXVK_STOCK, null)
        D3dTier.D3D12 -> LayerSelection(TranslationLayer.VKD3D_PROTON, d3d12Warning)
        D3dTier.UNKNOWN -> LayerSelection(TranslationLayer.DXVK_STOCK, null)
    }

    fun inferTier(hints: TierHints): D3dTier = when {
        hints.d3d12 -> D3dTier.D3D12
        hints.d3d11 -> D3dTier.D3D11
        hints.d3d9 -> D3dTier.D3D9
        else -> D3dTier.UNKNOWN
    }

    /** "11_1" -> 1101, "12_2" -> 1202; null when unparseable. */
    fun featureLevelRank(level: String?): Int? {
        val m = Regex("^(\\d+)_(\\d+)$").matchEntire(level?.trim().orEmpty()) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        if (major !in 9..16 || minor !in 0..9) return null
        return major * 100 + minor
    }

    fun isFeatureLevelAllowed(level: String?): Boolean {
        val rank = featureLevelRank(level) ?: return false
        return rank <= featureLevelRank(MALI_FEATURE_LEVEL_CAP)!!
    }

    fun shaderModelRank(model: String?): Int? {
        val m = Regex("^(\\d+)_(\\d+)$").matchEntire(model?.trim().orEmpty()) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        if (major !in 5..7 || minor !in 0..9) return null
        return major * 100 + minor
    }

    /**
     * Enforce the Mali verdicts on a launch environment. Upstream defaults ship
     * VKD3D_FEATURE_LEVEL=12_2 / VKD3D_SHADER_MODEL=6_9; on this GPU any level
     * above the cap is downgraded (never passed through), and missing cap keys
     * are filled with the safe values so a profile that omits them cannot
     * silently run uncapped.
     */
    fun clampEnvForMali(env: Map<String, String?>): Map<String, String?> {
        val out = env.toMutableMap()
        val level = out["VKD3D_FEATURE_LEVEL"]
        if (!isFeatureLevelAllowed(level)) out["VKD3D_FEATURE_LEVEL"] = MALI_FEATURE_LEVEL_CAP
        val model = out["VKD3D_SHADER_MODEL"]
        val modelRank = shaderModelRank(model)
        if (modelRank == null || modelRank > shaderModelRank(MALI_SHADER_MODEL_CAP)!!) {
            out["VKD3D_SHADER_MODEL"] = MALI_SHADER_MODEL_CAP
        }
        return out
    }
}
