package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ProtonSmartDefaultsTest {
    @Test fun d3d9DefaultsToDxvkSarek() {
        val sel = ProtonSmartDefaults.select(ProtonSmartDefaults.D3dTier.D3D9)
        assertEquals(ProtonSmartDefaults.TranslationLayer.DXVK_SAREK, sel.layer)
        assertNull(sel.d3d12Warning)
    }

    @Test fun d3d11DefaultsToStockDxvk() {
        val sel = ProtonSmartDefaults.select(ProtonSmartDefaults.D3dTier.D3D11)
        assertEquals(ProtonSmartDefaults.TranslationLayer.DXVK_STOCK, sel.layer)
        assertNull(sel.d3d12Warning)
    }

    @Test fun d3d12DefaultsToVkd3dProtonWithStretchWarning() {
        val sel = ProtonSmartDefaults.select(ProtonSmartDefaults.D3dTier.D3D12)
        assertEquals(ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON, sel.layer)
        val warning = sel.d3d12Warning
        assertNotNull(warning)
        assertEquals("11_1", warning!!.featureLevelCap)
        assertEquals("6_0", warning.shaderModelCap)
        assertTrue(warning.message.contains("11_1"))
        assertTrue(warning.message.contains("will not start"))
    }

    @Test fun unknownTierFallsBackToStockDxvkWithoutWarning() {
        val sel = ProtonSmartDefaults.select(ProtonSmartDefaults.D3dTier.UNKNOWN)
        assertEquals(ProtonSmartDefaults.TranslationLayer.DXVK_STOCK, sel.layer)
        assertNull(sel.d3d12Warning)
    }

    @Test fun tierInferencePrefersTheHighestHintedApi() {
        val h = ProtonSmartDefaults::inferTier
        assertEquals(ProtonSmartDefaults.D3dTier.D3D12,
            h(ProtonSmartDefaults.TierHints(d3d9 = true, d3d11 = true, d3d12 = true)))
        assertEquals(ProtonSmartDefaults.D3dTier.D3D11,
            h(ProtonSmartDefaults.TierHints(d3d9 = true, d3d11 = true, d3d12 = false)))
        assertEquals(ProtonSmartDefaults.D3dTier.D3D9,
            h(ProtonSmartDefaults.TierHints(d3d9 = true, d3d11 = false, d3d12 = false)))
        assertEquals(ProtonSmartDefaults.D3dTier.UNKNOWN,
            h(ProtonSmartDefaults.TierHints(d3d9 = false, d3d11 = false, d3d12 = false)))
    }

    @Test fun featureLevel12IsNeverAllowedOnMali() {
        assertTrue(ProtonSmartDefaults.isFeatureLevelAllowed("11_0"))
        assertTrue(ProtonSmartDefaults.isFeatureLevelAllowed("11_1"))
        assertFalse(ProtonSmartDefaults.isFeatureLevelAllowed("12_0"))
        assertFalse(ProtonSmartDefaults.isFeatureLevelAllowed("12_1"))
        assertFalse(ProtonSmartDefaults.isFeatureLevelAllowed("12_2"))
        assertFalse(ProtonSmartDefaults.isFeatureLevelAllowed("banana"))
        assertFalse(ProtonSmartDefaults.isFeatureLevelAllowed(""))
    }

    @Test fun maliClampDowngradesUpstreamDefaultsToTheVerdictCeiling() {
        // GameEnvironment.defaults() ships VKD3D_FEATURE_LEVEL=12_2 / VKD3D_SHADER_MODEL=6_9;
        // on this GPU those must never reach the game.
        val clamped = ProtonSmartDefaults.clampEnvForMali(mapOf(
            "VKD3D_FEATURE_LEVEL" to "12_2",
            "VKD3D_SHADER_MODEL" to "6_9",
            "DXVK_HUD" to "fps",
        ))
        assertEquals("11_1", clamped["VKD3D_FEATURE_LEVEL"])
        assertEquals("6_0", clamped["VKD3D_SHADER_MODEL"])
        assertEquals("fps", clamped["DXVK_HUD"])
    }

    @Test fun maliClampKeepsLevelsAtOrBelowTheCeiling() {
        val clamped = ProtonSmartDefaults.clampEnvForMali(mapOf(
            "VKD3D_FEATURE_LEVEL" to "11_0",
            "VKD3D_SHADER_MODEL" to "6_0",
        ))
        assertEquals("11_0", clamped["VKD3D_FEATURE_LEVEL"])
        assertEquals("6_0", clamped["VKD3D_SHADER_MODEL"])
    }

    @Test fun maliClampFillsMissingCapsWithSafeValues() {
        val clamped = ProtonSmartDefaults.clampEnvForMali(emptyMap())
        assertEquals("11_1", clamped["VKD3D_FEATURE_LEVEL"])
        assertEquals("6_0", clamped["VKD3D_SHADER_MODEL"])
    }

    @Test fun maliClampRepairsUnparseableLevelsInsteadOfPassingThemThrough() {
        val clamped = ProtonSmartDefaults.clampEnvForMali(mapOf("VKD3D_FEATURE_LEVEL" to "99_x"))
        assertEquals("11_1", clamped["VKD3D_FEATURE_LEVEL"])
    }

    @Test fun selectionIdsAreStableForPersistence() {
        assertEquals("dxvk-sarek", ProtonSmartDefaults.TranslationLayer.DXVK_SAREK.id)
        assertEquals("dxvk", ProtonSmartDefaults.TranslationLayer.DXVK_STOCK.id)
        assertEquals("vkd3d-proton", ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON.id)
        assertEquals(ProtonSmartDefaults.TranslationLayer.DXVK_SAREK,
            ProtonSmartDefaults.TranslationLayer.byId("dxvk-sarek"))
        assertNull(ProtonSmartDefaults.TranslationLayer.byId("wined3d"))
    }
}
