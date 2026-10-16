package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatTestSuiteTest {
    private val systemIds = listOf(
        "stage0-env", "vk-compute-test", "vk-render-test", "gamescope-headless",
        "vkcube-gamescope", "vkcube", "vulkaninfo", "d3d9-cube", "d3d11-cube",
    )

    @Test fun systemChecksComeFirstInOrder() {
        // Gate B2 order: Stage 0 probe, then headless compute (GPU bring-up,
        // no display), vkcube (needs display), vulkaninfo, D3D cubes.
        val suite = CompatTestSuite.build(installedGames = emptyList())
        val ids = suite.map { it.id }
        assertEquals(systemIds, ids)
        assertTrue(suite.all { it.kind == TestKind.SYSTEM_CHECK })
    }

    @Test fun computeTestNeedsNoDxvkVariant() {
        val byId = CompatTestSuite.build(emptyList()).associateBy { it.id }
        assertEquals(null, byId["vk-compute-test"]!!.dxvkVariant)
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
        assertEquals(11, suite.size)
        assertEquals(systemIds, suite.take(9).map { it.id })
        val gameSpecs = suite.drop(9)
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
        assertEquals(11, suite.size) // 9 system + 2 requested
        val hades = suite.first { it.id == "game-33" }
        assertEquals(33L, hades.gameId)
    }

    @Test fun stage0ProbeRunsFirst() {
        val first = CompatTestSuite.systemChecks().first()
        assertEquals("stage0-env", first.id)
        assertEquals(TestKind.SYSTEM_CHECK, first.kind)
    }

    @Test fun timeoutsAreSanePerKind() {
        for (s in CompatTestSuite.build(emptyList())) {
            assertTrue("${s.id} timeout ${s.timeoutSec}s out of range", s.timeoutSec in 15..180)
        }
        val game = CompatTestSuite.gameSpec(1L, "X")
        assertTrue(game.timeoutSec >= 120)
    }

    @Test fun defaultTierGamesMatchPhase6TierPlan() {
        // Mirrors docs/COMPAT.md: Tier 1 (light F2P) then Tier 2 (D3D11 F2P).
        val games = CompatTestSuite.defaultTierGames()
        assertEquals(7, games.size)
        assertTrue(games.all { it.kind == TestKind.GAME_TEST })
        assertEquals(
            listOf(440L, 304930L, 291550L, 230410L, 570L, 238960L, 444090L),
            games.map { it.gameId },
        )
        assertEquals(
            listOf(
                "Team Fortress 2", "Unturned", "Brawlhalla",
                "Warframe", "Dota 2", "Path of Exile", "Paladins",
            ),
            games.map { it.label },
        )
        assertTrue(games.all { it.timeoutSec >= 120 })
    }

    @Test fun defaultTierGamesBuildIntoRequestedSuite() {
        val requested = CompatTestSuite.defaultTierGames()
        val suite = CompatTestSuite.buildWithRequested(requested, installedIds = setOf(440L, 570L))
        assertEquals(9 + 7, suite.size) // 9 system checks + 7 requested
        assertTrue(CompatTestSuite.isInstalled(suite.first { it.id == "game-440" }, setOf(440L, 570L)))
    }

    @Test fun computeTestSpecHasSaneTimeout() {
        val spec = CompatTestSuite.build(emptyList()).first { it.id == "vk-compute-test" }
        assertEquals(TestKind.SYSTEM_CHECK, spec.kind)
        assertTrue("compute test timeout ${spec.timeoutSec}s out of range", spec.timeoutSec in 15..180)
    }
}
