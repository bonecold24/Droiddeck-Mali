package com.droiddeck.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 5: the FEX preset-selection logic used when x86/x86-64 binaries run
 * under FEX (the Steam client's game fallback path).
 */
class FexPresetTest {

    @Test fun unknownIdsFallBackToTheDefaultPreset() {
        assertEquals(emptyList<String>(), FexPreset.env("does-not-exist"))
        assertEquals(FexPreset.byId("").id, FexPreset.byId("does-not-exist").id)
    }

    @Test fun stabilityKeepsTSOOnAndMultiblockOff() {
        val env = FexPreset.environment("STABILITY")
        assertEquals("1", env["FEX_TSOENABLED"])
        assertEquals("1", env["FEX_VECTORTSOENABLED"])
        assertEquals("1", env["FEX_MEMCPYSETTSOENABLED"])
        assertEquals("1", env["FEX_HALFBARRIERTSOENABLED"])
        assertEquals("0", env["FEX_MULTIBLOCK"])
        assertEquals("0", env["FEX_X87REDUCEDPRECISION"])
    }

    @Test fun performanceDropsTSOAndEnablesMultiblock() {
        val env = FexPreset.environment("PERFORMANCE")
        assertEquals("0", env["FEX_TSOENABLED"])
        assertEquals("1", env["FEX_MULTIBLOCK"])
        assertEquals("1", env["FEX_X87REDUCEDPRECISION"])
    }

    @Test fun performanceTsoKeepsTheMainTSOButNotTheHelpers() {
        val env = FexPreset.environment("PERFORMANCE_TSO")
        assertEquals("1", env["FEX_TSOENABLED"])
        assertEquals("0", env["FEX_VECTORTSOENABLED"])
        assertEquals("0", env["FEX_MEMCPYSETTSOENABLED"])
        assertEquals("0", env["FEX_HALFBARRIERTSOENABLED"])
    }

    @Test fun extremeDisablesTheL2CacheAndTightensTheL1Heuristics() {
        val env = FexPreset.environment("EXTREME")
        assertEquals("none", env["FEX_SMCCHECKS"])
        assertEquals("1", env["FEX_DISABLEL2CACHE"])
        assertEquals("1", env["FEX_DYNAMICL1CACHE"])
        assertEquals("250", env["FEX_DYNAMICL1CACHEINCREASECOUNTHEURISTIC"])
        assertEquals("50", env["FEX_DYNAMICL1CACHEDECREASECOUNTHEURISTIC"])
    }

    @Test fun denuvoUsesFullSmcChecksAndHidesTheHypervisorBit() {
        val env = FexPreset.environment("DENUVO")
        assertEquals("full", env["FEX_SMCCHECKS"])
        assertEquals("1", env["FEX_HIDEHYPERVISORBIT"])
    }

    @Test fun environmentKeysCoverEveryPreset() {
        assertTrue(FexPreset.environmentKeys.contains("FEX_TSOENABLED"))
        assertTrue(FexPreset.environmentKeys.contains("FEX_MULTIBLOCK"))
        assertTrue(FexPreset.environmentKeys.contains("FEX_SMCCHECKS"))
        for (preset in FexPreset.all) {
            for (entry in preset.env) {
                val key = entry.substringBefore('=')
                assertTrue("preset ${preset.id} uses $key", FexPreset.environmentKeys.contains(key))
            }
        }
    }
}
