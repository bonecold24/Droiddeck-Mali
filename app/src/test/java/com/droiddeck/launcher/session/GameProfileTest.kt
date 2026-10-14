package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.PerfPresets
import com.droiddeck.launcher.core.ProtonSmartDefaults
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameProfileTest {
    private fun tempDir(): File = Files.createTempDirectory("profiles").toFile()

    private fun goodJson() = """
        {"version":1,"appId":"620","protonVersion":"proton-ge","dxvkVariant":"dxvk-sarek",
         "fexFlags":{"FEX_TSOENABLED":"1","FEX_MULTIBLOCK":"0"},
         "cpuPinning":"4,5,6","envVars":{"DXVK_HUD":"fps"},"frameLimiterFps":60,
         "preset":"performance","overlay":{"enabled":true,"showFps":true,"showFrameTimes":true}}
    """.trimIndent()

    @Test fun goodProfileParsesEveryField() {
        val p = GameProfile.parse(goodJson())!!
        assertEquals("620", p.appId)
        assertEquals("proton-ge", p.protonVersion)
        assertEquals(ProtonSmartDefaults.TranslationLayer.DXVK_SAREK, p.dxvkVariant)
        assertEquals(mapOf("FEX_TSOENABLED" to "1", "FEX_MULTIBLOCK" to "0"), p.fexFlags)
        assertEquals("4,5,6", p.cpuPinning)
        assertEquals(mapOf("DXVK_HUD" to "fps"), p.envVars)
        assertEquals(60, p.frameLimiterFps)
        assertEquals(PerfPresets.Preset.PERFORMANCE, p.preset)
        assertEquals(PerfOverlay.OverlayState(true, true, true), p.overlay)
    }

    @Test fun roundTripIsStable() {
        val p = GameProfile.parse(goodJson())!!
        assertEquals(p, GameProfile.parse(p.toJson()))
    }

    @Test fun garbageJsonReturnsNullAndStoreFallsBackToDefaults() {
        // parse() is strict; the store turns a corrupt file into safe defaults.
        assertNull(GameProfile.parse("{\"version\":1,\"appId\":\"620\","))
        val dir = tempDir()
        File(dir, "620.json").writeText("{\"version\":1,\"appId\":\"620\",")
        assertEquals(GameProfile(appId = "620"), GameProfileStore.load(dir, "620"))
    }

    @Test fun notJsonAtAllReturnsNull() {
        assertNull(GameProfile.parse("this is not json"))
        assertNull(GameProfile.parse(""))
    }

    @Test fun missingOrBadAppIdReturnsNull() {
        assertNull(GameProfile.parse("""{"version":1}"""))
        assertNull(GameProfile.parse("""{"version":1,"appId":"0"}"""))
        assertNull(GameProfile.parse("""{"version":1,"appId":"abc"}"""))
        assertNull(GameProfile.parse("""{"version":1,"appId":"620\n"}"""))
    }

    @Test fun wrongFieldTypesFallBackToSafeDefaults() {
        val p = GameProfile.parse("""
            {"version":1,"appId":"620","protonVersion":42,"dxvkVariant":true,
             "fexFlags":"nope","cpuPinning":123,"envVars":[1,2],"frameLimiterFps":"fast",
             "preset":7,"overlay":"yes"}
        """.trimIndent())!!
        assertEquals(GameProfile(appId = "620"), p)
    }

    @Test fun unknownProtonAndLayerIdsBecomeGlobalDefault() {
        val p = GameProfile.parse("""{"version":1,"appId":"620","protonVersion":"nope","dxvkVariant":"wined3d"}""")!!
        assertNull(p.protonVersion)
        assertNull(p.dxvkVariant)
    }

    @Test fun unsafeFexFlagsAreDropped() {
        val p = GameProfile.parse("""
            {"version":1,"appId":"620",
             "fexFlags":{"FEX_TSOENABLED":"1","FEX_SMCCHECKS":"none","FEX_DISABLEL2CACHE":"1","BOX64_X":"1"}}
        """.trimIndent())!!
        assertEquals(mapOf("FEX_TSOENABLED" to "1"), p.fexFlags)
    }

    @Test fun badEnvVarNamesAreDropped() {
        val p = GameProfile.parse("""
            {"version":1,"appId":"620",
             "envVars":{"DXVK_HUD":"fps","BOX64_DYNAREC":"1","has space":"1","":"x"}}
        """.trimIndent())!!
        assertEquals(mapOf("DXVK_HUD" to "fps"), p.envVars)
    }

    @Test fun cpuPinningIsSanitized() {
        val p = GameProfile.parse("""{"version":1,"appId":"620","cpuPinning":"6,4,abc,99,4"}""")!!
        assertEquals("4,6", p.cpuPinning)
    }

    @Test fun outOfRangeFrameLimiterIsDropped() {
        assertNull(GameProfile.parse("""{"version":1,"appId":"620","frameLimiterFps":0}""")!!.frameLimiterFps)
        assertNull(GameProfile.parse("""{"version":1,"appId":"620","frameLimiterFps":1000}""")!!.frameLimiterFps)
        assertEquals(120, GameProfile.parse("""{"version":1,"appId":"620","frameLimiterFps":120}""")!!.frameLimiterFps)
    }

    @Test fun storeRoundTrip() {
        val dir = tempDir()
        val p = GameProfile.parse(goodJson())!!
        GameProfileStore.save(dir, p)
        assertEquals(p, GameProfileStore.load(dir, "620"))
    }

    @Test fun storeMissingReturnsNull() {
        assertNull(GameProfileStore.load(tempDir(), "620"))
    }

    @Test fun storeCorruptFileYieldsSafeDefaults() {
        val dir = tempDir()
        File(dir, "620.json").writeText("{oops")
        assertEquals(GameProfile(appId = "620"), GameProfileStore.load(dir, "620"))
    }

    @Test fun storeRejectsBadAppIds() {
        val dir = tempDir()
        assertNull(GameProfileStore.load(dir, "../evil"))
        assertNull(GameProfileStore.load(dir, ""))
    }

    @Test fun storeDelete() {
        val dir = tempDir()
        GameProfileStore.save(dir, GameProfile(appId = "620"))
        assertTrue(GameProfileStore.delete(dir, "620"))
        assertNull(GameProfileStore.load(dir, "620"))
        assertFalse(GameProfileStore.delete(dir, "620"))
    }
}
