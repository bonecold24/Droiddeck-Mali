package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class FexSafeTuningTest {
    @Test fun exposesOnlyTheSafeSubset() {
        val keys = FexSafeTuning.knobs.map { it.key }.toSet()
        assertTrue(keys.contains("FEX_TSOENABLED"))
        assertTrue(keys.contains("FEX_MULTIBLOCK"))
        assertTrue(keys.contains("FEX_X87REDUCEDPRECISION"))
        assertTrue(keys.contains("FEX_SMCCHECKS"))
        assertTrue(keys.contains("FEX_HIDEHYPERVISORBIT"))
        // EXTREME-only knobs are deliberately not exposed.
        assertFalse(keys.contains("FEX_DISABLEL2CACHE"))
        assertFalse(keys.contains("FEX_DYNAMICL1CACHE"))
        assertFalse(keys.contains("FEX_DYNAMICL1CACHEINCREASECOUNTHEURISTIC"))
        // Box64 env is unsupported by GameEnvironment.supported(); never exposed here.
        assertTrue(keys.none { it.startsWith("BOX64_") })
    }

    @Test fun smcChecksNeverAllowsNone() {
        val knob = FexSafeTuning.knobs.first { it.key == "FEX_SMCCHECKS" }
        assertFalse(knob.safeValues.contains("none"))
        assertTrue(knob.safeValues.contains("full"))
    }

    @Test fun toEnvKeepsOnlySafeKeysAndValues() {
        val env = FexSafeTuning.toEnv(mapOf(
            "FEX_TSOENABLED" to "1",
            "FEX_MULTIBLOCK" to "1",
            "FEX_SMCCHECKS" to "full",
            "FEX_SMCCHECKS_BAD" to "1",
            "FEX_DISABLEL2CACHE" to "1",   // not in the safe subset
            "FEX_TSOENABLED_X" to "1",     // unknown key
            "BOX64_DYNAREC" to "1",        // box64 excluded
        ))
        assertEquals(mapOf(
            "FEX_TSOENABLED" to "1",
            "FEX_MULTIBLOCK" to "1",
            "FEX_SMCCHECKS" to "full",
        ), env)
    }

    @Test fun toEnvDropsUnsafeValues() {
        assertEquals(emptyMap<String, String>(), FexSafeTuning.toEnv(mapOf("FEX_SMCCHECKS" to "none")))
        assertEquals(emptyMap<String, String>(), FexSafeTuning.toEnv(mapOf("FEX_TSOENABLED" to "2")))
        assertEquals(mapOf("FEX_TSOENABLED" to "0"), FexSafeTuning.toEnv(mapOf("FEX_TSOENABLED" to "0")))
    }

    @Test fun knobDefaultsMatchFexUpstreamDefaults() {
        // Sanity: defaults must be FEX's own defaults so "reset" is a no-op.
        val byKey = FexSafeTuning.knobs.associateBy { it.key }
        assertEquals("0", byKey["FEX_TSOENABLED"]!!.default)
        assertEquals("1", byKey["FEX_MULTIBLOCK"]!!.default)
        assertEquals("1", byKey["FEX_X87REDUCEDPRECISION"]!!.default)
    }

    @Test fun emptyInputYieldsEmptyEnv() {
        assertEquals(emptyMap<String, String>(), FexSafeTuning.toEnv(emptyMap()))
    }
}
