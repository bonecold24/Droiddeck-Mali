package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class CpuAllocationDefaultsTest {
    private val thor = (0..7).associateWith { when (it) { in 0..2 -> 2.016; in 3..6 -> 2.8032; else -> 3.1872 } }

    @Test fun gen2UsesTheFourMiddleCoresAndOnePrime() {
        for (model in listOf("SM8550", "QCS8550", "sm8550-ac")) {
            assertEquals(CpuAllocationDefaults.Allocation("7", "3,4,5,6"), CpuAllocationDefaults.forDevice(model, "", thor))
        }
        assertEquals(CpuAllocationDefaults.Allocation("7", "3,4,5,6"), CpuAllocationDefaults.forDevice("", "kalama", thor))
    }

    @Test fun coreNumbersAndClockVariantsAreDetectedInsteadOfHardcoded() {
        val reordered = mapOf(0 to 3.36, 1 to 2.0, 2 to 2.0, 3 to 2.0, 4 to 2.8, 5 to 2.8, 6 to 2.8, 7 to 2.8)
        assertEquals(CpuAllocationDefaults.Allocation("0", "4,5,6,7"), CpuAllocationDefaults.forDevice("SM8550-AC", "kalama", reordered))
    }

    @Test fun anotherOrUnknownChipDoesNotAcquireThisDefault() {
        assertNull(CpuAllocationDefaults.forDevice("SM8650", "pineapple", thor))
        assertNull(CpuAllocationDefaults.forDevice("SM8650", "kalama", thor))
        assertNull(CpuAllocationDefaults.forDevice("", "", thor))
    }

    @Test fun missingOrUnexpectedTopologyKeepsTheSchedulerDefault() {
        assertNull(CpuAllocationDefaults.forDevice("SM8550", "kalama", thor - 7))
        assertNull(CpuAllocationDefaults.forDevice("SM8550", "kalama", thor + (7 to null)))
        assertNull(CpuAllocationDefaults.forDevice("SM8550", "kalama", thor + (7 to 0.0)))
        assertNull(CpuAllocationDefaults.forDevice("SM8550", "kalama", thor + (7 to Double.NaN)))
        assertNull(CpuAllocationDefaults.forDevice("SM8550", "kalama", (0..7).associateWith { 2.8 }))
    }

    @Test fun dimensity9300PlusPinsClientToPrimeAndGameToMidCluster() {
        // 14T Pro layout: 4x A520 @ 2.0 GHz (0-3), 3x A720 @ 2.85 GHz (4-6), 1x X4 @ 3.4 GHz (7).
        val d9300p = (0..7).associateWith { when (it) { in 0..3 -> 2.0; in 4..6 -> 2.85; else -> 3.4 } }
        assertEquals(CpuAllocationDefaults.Allocation("7", "4,5,6"),
            CpuAllocationDefaults.forDevice("MT6989", "", d9300p))
        assertEquals(CpuAllocationDefaults.Allocation("7", "4,5,6"),
            CpuAllocationDefaults.forDevice("", "mt6989", d9300p))
        assertEquals(CpuAllocationDefaults.Allocation("7", "4,5,6"),
            CpuAllocationDefaults.forDevice("mt6989", "MT6989", d9300p))
        // Cluster detection follows frequencies, not core numbers.
        val reordered = mapOf(0 to 3.4, 1 to 2.0, 2 to 2.0, 3 to 2.0, 4 to 2.0, 5 to 2.85, 6 to 2.85, 7 to 2.85)
        assertEquals(CpuAllocationDefaults.Allocation("0", "5,6,7"),
            CpuAllocationDefaults.forDevice("MT6989", "", reordered))
    }

    @Test fun dimensity9300PlusRejectsWrongTopologies() {
        val thor = (0..7).associateWith { when (it) { in 0..2 -> 2.016; in 3..6 -> 2.8032; else -> 3.1872 } }
        // Snapdragon 8 Gen 2 cluster shape on a 9300+ chip id: no default.
        assertNull(CpuAllocationDefaults.forDevice("MT6989", "", thor))
        assertNull(CpuAllocationDefaults.forDevice("MT6989", "kalama", thor))
    }

    @Test fun allCoreGamesUndoTheClientsInheritedRestriction() {
        val all = CpuCores.listOrAll("")
        assertEquals(all, CpuCores.gameLaunchList("", clientPinned = true))
        assertEquals(all, CpuCores.gameLaunchList(all, clientPinned = true))
        assertEquals("", CpuCores.gameLaunchList("", clientPinned = false))
        assertEquals("0", CpuCores.gameLaunchList("0", clientPinned = true))
    }
}
