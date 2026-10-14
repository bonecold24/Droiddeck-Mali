package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTestSuiteTest {
    @Test fun systemChecksComeFirstInOrder() {
        val suite = CompatTestSuite.build(installedGames = emptyList())
        val ids = suite.map { it.id }
        assertEquals(listOf("vkcube", "vulkaninfo", "d3d9-cube", "d3d11-cube"), ids)
        assertTrue(suite.all { it.kind == TestKind.SYSTEM_CHECK })
    }

    @Test fun systemChecksCarryExpectedDxvkVariants() {
        val byId = CompatTestSuite.build(emptyList()).associateBy { it.id }
        assertEquals(null, byId["vkcube"]!!.dxvkVariant)
        assertEquals("DXVK-Sarek", byId["d3d9-cube"]!!.dxvkVariant)
        assertEquals("stock DXVK", byId["d3d11-cube"]!!.dxvkVariant)
    }

    @Test fun installedGamesAreAppendedAfterSystemChecks() {
        val games = listOf(
            CompatTestSuite.InstalledGame(gameId = 11L, name = "Hollow Knight"),
            CompatTestSuite.InstalledGame(gameId = 22L, name = "Stardew Valley"),
        )
        val suite = CompatTestSuite.build(games)
        assertEquals(6, suite.size)
        assertEquals(listOf("vkcube", "vulkaninfo", "d3d9-cube", "d3d11-cube"), suite.take(4).map { it.id })
        val gameSpecs = suite.drop(4)
        assertTrue(gameSpecs.all { it.kind == TestKind.GAME_TEST })
        assertEquals(listOf(11L, 22L), gameSpecs.map { it.gameId })
        assertEquals(listOf("Hollow Knight", "Stardew Valley"), gameSpecs.map { it.label })
    }

    @Test fun missingRequestedGamesStayInSuiteForGracefulSkip() {
        // Requested (tier) games that are NOT installed must remain as specs so the
        // executor can record SKIPPED instead of the suite failing or dropping them silently.
        val requested = listOf(
            CompatTestSuite.gameSpec(11L, "Hollow Knight"),
            CompatTestSuite.gameSpec(33L, "Hades"),
        )
        val suite = CompatTestSuite.buildWithRequested(requested, installedIds = setOf(11L))
        assertEquals(6, suite.size) // 4 system + 2 requested
        val hades = suite.first { it.id == "game-33" }
        assertEquals(33L, hades.gameId)
    }

    @Test fun timeoutsAreSanePerKind() {
        for (s in CompatTestSuite.build(emptyList())) {
            assertTrue("${s.id} timeout ${s.timeoutSec}s out of range", s.timeoutSec in 15..120)
        }
        val game = CompatTestSuite.gameSpec(1L, "X")
        assertTrue(game.timeoutSec >= 120)
    }
}
